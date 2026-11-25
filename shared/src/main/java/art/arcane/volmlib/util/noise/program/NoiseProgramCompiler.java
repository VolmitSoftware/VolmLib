package art.arcane.volmlib.util.noise.program;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.noise.CNG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class NoiseProgramCompiler {
    final boolean volumetric;
    final List<NodeEntry> nodes = new ArrayList<>();
    final List<TermEntry> terms = new ArrayList<>();
    private final IdentityHashMap<CNG, Integer> nodeIndex = new IdentityHashMap<>();
    private final Map<Long, Integer> termIndex = new HashMap<>();
    private final Set<Long> termsInProgress = new HashSet<>();

    private NoiseProgramCompiler(boolean volumetric) {
        this.volumetric = volumetric;
    }

    static NoiseProgram compile(CNG root, boolean volumetric) {
        NoiseProgramCompiler compiler = new NoiseProgramCompiler(volumetric);
        int rootNode = compiler.node(Objects.requireNonNull(root, "root"));
        if (volumetric) {
            compiler.term(rootNode, NoiseProgram.SIGNED_3D, 0, 1, 2);
        } else {
            compiler.term(rootNode, NoiseProgram.UNSIGNED_2D, 0, 1, 0);
        }
        return new NoiseProgram(compiler);
    }

    static int pairId(int first, int second) {
        return first + second - 1;
    }

    static boolean isVolumetricMode(int mode) {
        return mode >= NoiseProgram.UNSIGNED_3D;
    }

    long signature() {
        SignatureHasher hasher = new SignatureHasher().add(volumetric).add(nodes.size()).add(terms.size());
        for (NodeEntry node : nodes) {
            if (node.opaque) {
                hasher.add(SignatureHasher.unique());
                continue;
            }
            hasher.add(node.kernel.signature())
                    .add(node.scale)
                    .add(node.fractureScale)
                    .add(node.signedFractureScale)
                    .add(node.opacity)
                    .add(node.power)
                    .add(node.up)
                    .add(node.down)
                    .add(node.patch)
                    .add(node.unityOpacity)
                    .add(node.identityPost)
                    .add(node.identitySigned)
                    .add(node.injector == null ? -1 : node.injector.ordinal())
                    .add(node.fracture);
            if (node.children != null) {
                for (int child : node.children) {
                    hasher.add(child);
                }
            }
        }
        for (TermEntry term : terms) {
            hasher.add(term.mode)
                    .add(term.node)
                    .add(term.axis0)
                    .add(term.axis1)
                    .add(term.axis2)
                    .add(term.dep0)
                    .add(term.dep1)
                    .add(term.dep2)
                    .add(term.applyPost);
            if (term.children != null) {
                for (int child : term.children) {
                    hasher.add(child);
                }
            }
        }
        return hasher.value();
    }

    boolean representable() {
        for (TermEntry term : terms) {
            NodeEntry node = nodes.get(term.node);
            if (node.opaque || !node.kernel.kind().isKernel()) {
                return false;
            }
            if (term.applyPost && (node.power != 1D || (node.injector != null && node.injector.usesJavaMath()))) {
                return false;
            }
        }
        return true;
    }

    private int node(CNG cng) {
        Integer existing = nodeIndex.get(cng);
        if (existing != null) {
            return existing;
        }
        NodeEntry entry = new NodeEntry(cng);
        int index = nodes.size();
        nodes.add(entry);
        nodeIndex.put(cng, index);
        entry.opaque = isOpaque(cng);
        if (entry.opaque) {
            return index;
        }
        entry.kernel = PrimitiveKernel.of(cng.getGenerator());
        entry.scale = cng.getEffectiveScale();
        entry.fractureScale = cng.getFscale();
        entry.signedFractureScale = cng.getSignedFractureScale();
        entry.opacity = cng.getOpacity();
        entry.power = cng.getPower();
        entry.up = cng.getUp();
        entry.down = cng.getDown();
        entry.patch = cng.getPatch();
        entry.unityOpacity = cng.isUnityOpacity();
        entry.identityPost = cng.isIdentityPostFastPath();
        entry.identitySigned = cng.isIdentitySignedFastPath();
        if (cng.getFracture() != null && !cng.isNoscale()) {
            entry.fractureSource = cng.getFracture();
            entry.fracture = node(entry.fractureSource);
        }
        KList<CNG> children = cng.getChildren();
        if (children != null && !children.isEmpty()) {
            entry.injector = ChildInjector.of(cng.getInjector());
            int[] childNodes = new int[children.size()];
            for (int child = 0; child < childNodes.length; child++) {
                childNodes[child] = node(children.get(child));
            }
            entry.children = childNodes;
        }
        return index;
    }

    private static boolean isOpaque(CNG cng) {
        if (cng.getClass() != CNG.class || cng.getGenerator() == null || cng.getCache() != null) {
            return true;
        }
        KList<CNG> children = cng.getChildren();
        if (children == null || children.isEmpty()) {
            return false;
        }
        if (ChildInjector.of(cng.getInjector()) == null) {
            return true;
        }
        for (CNG child : children) {
            if (child == null) {
                return true;
            }
        }
        return false;
    }

    private int term(int node, int mode, int axis0, int axis1, int axis2) {
        long key = ((long) node << 16) | ((long) mode << 8) | ((long) axis0 << 4) | ((long) axis1 << 2) | axis2;
        Integer existing = termIndex.get(key);
        if (existing != null) {
            return existing;
        }
        if (!termsInProgress.add(key)) {
            throw new IllegalArgumentException("Noise graph is cyclic");
        }
        NodeEntry entry = nodes.get(node);
        TermEntry term = new TermEntry(mode, node, axis0, axis1, axis2);
        if (!entry.opaque) {
            resolveDependencies(entry, term);
        }
        termsInProgress.remove(key);
        int index = terms.size();
        terms.add(term);
        termIndex.put(key, index);
        return index;
    }

    private void resolveDependencies(NodeEntry entry, TermEntry term) {
        int fracture = entry.fracture;
        int a0 = term.axis0;
        int a1 = term.axis1;
        int a2 = term.axis2;
        switch (term.mode) {
            case NoiseProgram.UNSIGNED_2D, NoiseProgram.NOISE_2D -> {
                if (fracture >= 0) {
                    term.dep0 = term(fracture, NoiseProgram.UNSIGNED_2D, a0, a1, 0);
                    term.dep1 = term(fractureNode(entry, 1), NoiseProgram.UNSIGNED_2D, a0, a1, 0);
                }
                term.applyPost = term.mode == NoiseProgram.NOISE_2D || !entry.identityPost;
                if (term.applyPost && entry.children != null) {
                    term.children = childTerms(entry, NoiseProgram.NOISE_2D, a0, a1, 0);
                }
            }
            case NoiseProgram.SIGNED_2D -> {
                if (!entry.identitySigned) {
                    term.dep0 = term(term.node, NoiseProgram.UNSIGNED_2D, a0, a1, 0);
                } else if (fracture >= 0) {
                    term.dep0 = term(fracture, NoiseProgram.SIGNED_2D, a0, a1, 0);
                    term.dep1 = term(fractureNode(entry, 1), NoiseProgram.SIGNED_2D, a0, a1, 0);
                }
            }
            case NoiseProgram.UNSIGNED_3D, NoiseProgram.NOISE_3D -> {
                if (fracture >= 0) {
                    term.dep0 = term(fracture, NoiseProgram.UNSIGNED_3D, a0, a1, a2);
                    term.dep1 = term(fractureNode(entry, 1), NoiseProgram.UNSIGNED_2D, a0, a1, 0);
                    term.dep2 = term(fractureNode(entry, 2), NoiseProgram.UNSIGNED_3D, a0, a1, a2);
                }
                term.applyPost = term.mode == NoiseProgram.NOISE_3D || !entry.identityPost;
                if (term.applyPost && entry.children != null) {
                    term.children = childTerms(entry, NoiseProgram.NOISE_3D, a0, a1, a2);
                }
            }
            default -> {
                if (!entry.identitySigned) {
                    term.dep0 = term(term.node, NoiseProgram.UNSIGNED_3D, a0, a1, a2);
                } else if (fracture >= 0) {
                    term.dep0 = term(fracture, NoiseProgram.SIGNED_3D, a0, a1, a2);
                    term.dep1 = term(fractureNode(entry, 1), NoiseProgram.SIGNED_2D, a0, a1, 0);
                    term.dep2 = term(fractureNode(entry, 2), NoiseProgram.SIGNED_3D, a0, a1, a2);
                }
            }
        }
    }

    private int fractureNode(NodeEntry entry, int axis) {
        return node(entry.fractureSource.reseededForAxis(axis));
    }

    private int[] childTerms(NodeEntry entry, int mode, int axis0, int axis1, int axis2) {
        int[] childTerms = new int[entry.children.length];
        for (int child = 0; child < childTerms.length; child++) {
            childTerms[child] = term(entry.children[child], mode, axis0, axis1, axis2);
        }
        return childTerms;
    }

    static final class NodeEntry {
        final CNG source;
        PrimitiveKernel kernel;
        boolean opaque;
        double scale;
        double fractureScale;
        double signedFractureScale;
        double opacity;
        double power = 1D;
        double up;
        double down;
        double patch = 1D;
        boolean unityOpacity;
        boolean identityPost;
        boolean identitySigned;
        ChildInjector injector;
        CNG fractureSource;
        int fracture = -1;
        int[] children;

        NodeEntry(CNG source) {
            this.source = source;
        }
    }

    static final class TermEntry {
        final int mode;
        final int node;
        final int axis0;
        final int axis1;
        final int axis2;
        int dep0 = -1;
        int dep1 = -1;
        int dep2 = -1;
        int[] children;
        boolean applyPost;

        TermEntry(int mode, int node, int axis0, int axis1, int axis2) {
            this.mode = mode;
            this.node = node;
            this.axis0 = axis0;
            this.axis1 = axis1;
            this.axis2 = axis2;
        }
    }
}
