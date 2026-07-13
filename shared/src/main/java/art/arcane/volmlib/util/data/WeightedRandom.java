package art.arcane.volmlib.util.data;

import art.arcane.volmlib.util.collection.KeyPair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class WeightedRandom<T> {
    protected final List<KeyPair<T, Integer>> weightedObjects = new ArrayList<>();
    protected final Random random;
    protected int totalWeight = 0;

    public WeightedRandom(Random random) {
        this.random = random;
    }

    public WeightedRandom() {
        this.random = new Random();
    }

    public void put(T object, int weight) {
        weightedObjects.add(new KeyPair<>(object, weight));
        totalWeight += weight;
    }

    public WeightedRandom<T> merge(WeightedRandom<T> other) {
        weightedObjects.addAll(other.weightedObjects);
        totalWeight += other.totalWeight;
        return this;
    }

    public T pullRandom() {
        return pullRandom(random);
    }

    public T pullRandom(Random source) {
        int pull = source.nextInt(totalWeight);
        for (KeyPair<T, Integer> weightedObject : weightedObjects) {
            pull -= weightedObject.getV();
            if (pull < 0) {
                return weightedObject.getK();
            }
        }
        return weightedObjects.getLast().getK();
    }

    public int getSize() {
        return weightedObjects.size();
    }

    public void shuffle() {
        Collections.shuffle(weightedObjects, random);
    }
}
