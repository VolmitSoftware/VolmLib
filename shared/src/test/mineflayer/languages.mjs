import { readFile, stat } from 'node:fs/promises'
import path from 'node:path'

function commandValues(value) {
  if (value === null || typeof value !== 'object') {
    return []
  }
  const values = value.action === 'run_command' ? [value.value ?? value.command] : []
  for (const child of Object.values(value)) {
    values.push(...commandValues(child))
  }
  return values.filter(value => typeof value === 'string')
}

export default {
  name: 'languages',
  description: 'Validate local language scopes, shared server defaults, permission preflight, and command ownership.',
  async run(context) {
    const { bot, expect, step, waitForMessage } = context
    const timeout = 30000
    const mode = context.options.command ?? 'scopes'
    expect(['scopes', 'seed-denial', 'deny-global', 'provider-admins', 'reload', 'fallback'].includes(mode),
      'Unknown shared language scenario', mode)
    const command = async (text, expected) => {
      await context.sleep(1100)
      return context.command(text, expected, timeout)
    }
    const globalLanguage = async locale => {
      await context.sleep(1100)
      const responses = ['BileTools', 'ShapedPortals'].map(name =>
        waitForMessage(`${name}: server language is now ${locale}.`, timeout))
      bot.chat(`/volmit plugins languages ${locale}`)
      await Promise.all(responses)
    }
    const collectMenu = async (text, expected) => {
      const packets = []
      const collect = message => packets.push(message.json)
      bot.on('message', collect)
      try {
        await command(text, expected)
        await context.sleep(150)
        return packets.flatMap(commandValues)
      } finally {
        bot.removeListener('message', collect)
      }
    }
    const checkBothDefaults = async locale => {
      await command('/sp language server', `Current: ${locale}`)
      await command('/biletools language server', `Current: ${locale}`)
    }

    await step('confirm the player remains a non-operator', async () => {
      await command('/minecraft:seed', /permission|Unknown or incomplete command|Unknown command/i)
    })
    if (mode === 'scopes') {
      await step('offer only local scope actions from plugin language pickers and completions', async () => {
        for (const root of ['sp', 'biletools']) {
          const completions = await bot.tabComplete(`/${root} language `, true, false, timeout)
          const values = completions.map(value => typeof value === 'string' ? value : value.match)
          expect(values.length === 2 && values.includes('self') && values.includes('server'),
            'Plugin language completion does not expose the expected local scopes', { root, values })
          const commands = await collectMenu(`/${root} language`, 'Current: en_US')
          expect(commands.some(value => value.startsWith(`/${root} language self`)),
            'Plugin language picker has no personal language action', { root, commands })
          expect(commands.some(value => value === `/${root} language server`),
            'Plugin language picker has no server language action', { root, commands })
          expect(commands.every(value => value.startsWith(`/${root} language self`)
              || value.startsWith(`/${root} language server`)),
            'Plugin language picker contains an action outside its local scopes', { root, commands })
        }
      })
      await step('keep personal and server selection local to each plugin', async () => {
        await command('/sp language self fr_FR', 'ShapedPortals: your language is now fr_FR.')
        await command('/biletools language self', 'Current: en_US')
        await command('/sp language server fr_FR', 'ShapedPortals: server language is now fr_FR.')
        await command('/biletools language server', 'Current: en_US')
        await command('/biletools language server fr_FR', 'BileTools: server language is now fr_FR.')
        await command('/biletools language self fr_FR', 'BileTools: your language is now fr_FR.')
        await checkBothDefaults('fr_FR')
      })
      await step('offer global server language actions through the shared command', async () => {
        const commands = await collectMenu('/volmit plugins languages', 'Current: fr_FR')
        expect(commands.includes('/volmit plugins languages en_US'),
          'Shared language picker has no English server selection', commands)
        expect(commands.every(value => value.startsWith('/volmit plugins languages ')
            && !/\s(self|server)\b/.test(value)),
          'Shared language picker contains a personal or plugin-local action', commands)
        const completions = await bot.tabComplete('/volmit plugins languages ', true, false, timeout)
        const values = completions.map(value => typeof value === 'string' ? value : value.match)
        expect(values.includes('en_US') && !values.includes('self') && !values.includes('server'),
          'Shared language completion does not expose server locale choices', values)
      })
      await step('change both server defaults without replacing personal overrides', async () => {
        await globalLanguage('en_US')
        await checkBothDefaults('en_US')
        await command('/sp language self', 'Current: fr_FR')
        await command('/biletools language self', 'Current: fr_FR')
        await command('/sp status', /Création/)
      })
    } else if (mode === 'seed-denial') {
      await step('prepare distinct server defaults for permission preflight verification', async () => {
        await globalLanguage('fr_FR')
        await checkBothDefaults('fr_FR')
      })
    } else if (mode === 'deny-global') {
      await step('reject a global selection before writing any provider configuration', async () => {
        await command('/sp language self', 'Current: fr_FR')
        await command('/biletools language self', 'Current: fr_FR')
        const consumer = path.dirname(path.dirname(path.dirname(context.server.logPath)))
        const plugins = path.join(consumer, 'instances', context.server.instance, 'plugins')
        const files = [path.join(plugins, 'BileTools', 'biletools.yml'), path.join(plugins, 'ShapedPortals', 'config.toml')]
        const before = await Promise.all(files.map(async file => ({ content: await readFile(file, 'utf8'), mtime: (await stat(file)).mtimeMs })))
        const messages = []
        const collect = message => messages.push(message)
        bot.on('messagestr', collect)
        try {
          await command('/volmit plugins languages en_US', /permission/i)
          await context.sleep(250)
          expect(!messages.some(message => /Preparing language|server language is now/.test(message)),
            'A rejected shared selection started a provider update', messages)
        } finally {
          bot.removeListener('messagestr', collect)
        }
        const after = await Promise.all(files.map(async file => ({ content: await readFile(file, 'utf8'), mtime: (await stat(file)).mtimeMs })))
        expect(JSON.stringify(before) === JSON.stringify(after), 'A rejected shared selection wrote a provider configuration')
        await command('/sp language self', 'Current: fr_FR')
        await command('/biletools language self', 'Current: fr_FR')
      })
    } else if (mode === 'provider-admins') {
      await step('allow shared server selection through every provider administrator permission', async () => {
        await globalLanguage('en_US')
        await checkBothDefaults('en_US')
      })
    } else if (mode === 'fallback') {
      const consumer = path.dirname(path.dirname(path.dirname(context.server.logPath)))
      const plugins = path.join(consumer, 'instances', context.server.instance, 'plugins')
      const messages = []
      const collect = message => messages.push(message)
      bot.on('messagestr', collect)
      try {
        await step('replace incomplete personal language selections with persisted English', async () => {
          for (const [root, name] of [['sp', 'ShapedPortals'], ['biletools', 'BileTools']]) {
            await command(`/${root} language self fr_FR`, `${name}: your language is now fr_FR.`)
            await command(`/${root} language self de_DE`, `${name}: de_DE is unavailable; using English (en_US).`)
            await command(`/${root} language self`, 'Current: en_US')
            const preferences = await readFile(path.join(plugins, name, 'language-preferences.properties'), 'utf8')
            expect(preferences.split('\n').includes(`${bot.player.uuid}=en_US`),
              'Personal language fallback did not persist English', name)
          }
        })
        await step('fall back each server default independently while preserving personal overrides', async () => {
          await globalLanguage('fr_FR')
          for (const [root, name] of [['sp', 'ShapedPortals'], ['biletools', 'BileTools']]) {
            await command(`/${root} language self fr_FR`, `${name}: your language is now fr_FR.`)
            await command(`/${root} language server de_DE`, `${name}: de_DE is unavailable; using English (en_US).`)
            await command(`/${root} language server`, 'Current: en_US')
            await command(`/${root} language self`, 'Current: fr_FR')
          }
          await checkBothDefaults('en_US')
        })
        await step('apply English globally when a selected pack is incomplete without replacing personal overrides', async () => {
          await globalLanguage('fr_FR')
          await context.sleep(1100)
          const responses = ['BileTools', 'ShapedPortals'].map(name =>
            waitForMessage(`${name}: de_DE is unavailable; using English (en_US).`, timeout))
          bot.chat('/volmit plugins languages de_DE')
          await Promise.all(responses)
          await checkBothDefaults('en_US')
          await command('/sp language self', 'Current: fr_FR')
          await command('/biletools language self', 'Current: fr_FR')
          for (const file of [path.join(plugins, 'BileTools', 'biletools.yml'), path.join(plugins, 'ShapedPortals', 'config.toml')]) {
            const configuration = await readFile(file, 'utf8')
            expect(/language\s*[:=]\s*["']?en_US/.test(configuration), 'Server fallback did not persist English', path.basename(file))
          }
          expect(!messages.some(message => message.includes('language is now de_DE')),
            'An incomplete locale was incorrectly reported as active', messages)
        })
      } finally {
        bot.removeListener('messagestr', collect)
      }
    } else {
      await step('keep the shared command available after reloading ShapedPortals', async () => {
        await command('/biletools reload ShapedPortals', /Reloaded ShapedPortals|Rechargé : ShapedPortals/)
        await globalLanguage('fr_FR')
        await checkBothDefaults('fr_FR')
        await command('/sp language self', 'Current: fr_FR')
      })
      await step('hand off shared command ownership across a BileTools reload', async () => {
        const before = (await readFile(context.server.logPath, 'utf8')).length
        await command('/biletools reload BileTools', /Reloading BileTools|Rechargement en cours : BileTools/)
        const deadline = Date.now() + timeout
        let enabled = false
        while (Date.now() < deadline) {
          const log = (await readFile(context.server.logPath, 'utf8')).slice(before)
          if (log.includes('Enabling BileTools') && log.includes('Runtime platform: FOLIA')) {
            enabled = true
            break
          }
          await context.sleep(100)
        }
        expect(enabled, 'BileTools did not complete its targeted reload before the timeout')
        await globalLanguage('en_US')
        await checkBothDefaults('en_US')
        await command('/sp language self', 'Current: fr_FR')
        await command('/biletools language self', 'Current: fr_FR')
      })
    }
    expect(bot.entity !== undefined && bot.health > 0, 'Player did not remain active after shared language checks')
  }
}
