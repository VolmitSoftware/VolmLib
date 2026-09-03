import { readFile, stat } from 'node:fs/promises'
import { createRequire } from 'node:module'
import path from 'node:path'

function commandValues(value) {
  if (value === null || typeof value !== 'object') return []
  const values = value.action === 'run_command' ? [value.value ?? value.command] : []
  for (const child of Object.values(value)) values.push(...commandValues(child))
  return values.filter(value => typeof value === 'string')
}

export default {
  name: 'language-editor',
  description: 'Edit native language messages through inventory menus with private chat, permission, persistence, and reload checks.',
  async run(context) {
    const { bot, expect, step } = context
    const timeout = 30000
    const mode = context.options.command ?? 'edit'
    expect(['deny', 'edit', 'expiry', 'reload'].includes(mode), 'Unknown editor scenario mode', mode)
    const consumer = path.dirname(path.dirname(path.dirname(context.server.logPath)))
    const workspace = path.dirname(path.dirname(consumer))
    const plugins = path.join(consumer, 'instances', context.server.instance, 'plugins')
    const require = createRequire(path.join(workspace, 'MultiplexorApp/tool/mineflayer/package.json'))
    const ChatMessage = require('prismarine-chat')(bot.registry)
    const mineflayer = require('mineflayer')
    const text = value => value == null ? '' : ChatMessage.fromNotch(value).toString().replace(/§./g, '')
    const itemName = item => item == null ? '' : text(item.customName ?? item.displayName)
    const lore = item => (item?.customLore ?? []).map(text).join('\n')
    const title = () => text(bot.currentWindow?.title)
    const snapshot = async file => {
      try {
        return { content: await readFile(file, 'utf8'), mtime: (await stat(file)).mtimeMs }
      } catch (error) {
        if (error.code === 'ENOENT') return null
        throw error
      }
    }
    const shapedFrench = path.join(plugins, 'ShapedPortals/languages/fr_FR.toml')
    const bileFrench = path.join(plugins, 'BileTools/languages/overrides/fr_FR.yml')
    const selectionFiles = [
      path.join(plugins, 'ShapedPortals/config.toml'),
      path.join(plugins, 'BileTools/biletools.yml'),
      ...['ShapedPortals', 'BileTools'].map(name => path.join(plugins, name, 'language-preferences.properties'))
    ]
    const unchanged = async (file, before, message) => {
      expect(JSON.stringify(await snapshot(file)) === JSON.stringify(before), message, file)
    }
    const command = async (value, expected) => {
      await context.sleep(1100)
      return context.command(value, expected, timeout)
    }
    const until = async (predicate, message, limit = timeout) => {
      const deadline = Date.now() + limit
      while (Date.now() < deadline) {
        if (predicate()) return
        await context.sleep(50)
      }
      expect(false, message, { title: title(), slots: bot.currentWindow?.slots.slice(0, 54).map(itemName) })
    }
    const ready = async expected => {
      await until(() => title() === expected && itemName(bot.currentWindow?.slots[49]) === 'Refresh',
        `Editor window did not open: ${expected}`)
      expect(bot.currentWindow.inventoryStart === 54, 'Editor does not have 54 slots')
      return bot.currentWindow
    }
    const open = async (root, plugin, locale = null) => {
      await command(`/${root} language server edit${locale === null ? '' : ` ${locale}`}`)
      return ready(`${plugin} - ${locale ?? 'Language editor'}`)
    }
    const close = async () => {
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
      await context.sleep(150)
    }
    const click = async (slot, button = 0, clickMode = 0) => {
      const previous = bot.currentWindow
      await bot.clickWindow(slot, button, clickMode)
      return previous
    }
    const newWindow = async previous => {
      await until(() => bot.currentWindow != null && bot.currentWindow !== previous
        && itemName(bot.currentWindow.slots[49]) === 'Refresh', 'Editor did not replace its window')
      return bot.currentWindow
    }
    const privateInputs = new Set()
    const chatInput = async input => {
      if (input !== 'cancel') privateInputs.add(input)
      await context.sleep(1100)
      bot.chat(input)
    }
    const prompt = async (slot, expected, clickMode = 0) => {
      const pending = context.waitForMessage(expected, timeout)
      await click(slot, 0, clickMode)
      await pending
      await until(() => bot.currentWindow === null, 'Private editor prompt did not close the inventory')
    }
    const search = async (filter, expectedKey) => {
      await prompt(48, 'Search message keys or text.')
      await chatInput(filter)
      await until(() => itemName(bot.currentWindow?.slots[0]) === expectedKey
        && itemName(bot.currentWindow?.slots[50]) === 'Clear search', 'Message search did not find the expected key')
    }
    const save = async (input, locale = 'fr_FR') => {
      const saved = context.waitForMessage(`Saved ${locale}. Language selections are unchanged.`, timeout)
      await chatInput(input)
      await saved
      await until(() => bot.currentWindow != null && itemName(bot.currentWindow.slots[49]) === 'Refresh',
        'Editor did not reopen after saving')
    }
    const inventory = () => JSON.stringify(bot.inventory.items().map(item => ({ name: item.name, count: item.count, slot: item.slot })))
    const observerMessages = []
    let quitting = false
    let rejectObserver
    const observerFailure = new Promise((resolve, reject) => { rejectObserver = reject })
    observerFailure.catch(() => {})
    const observer = mineflayer.createBot({ host: context.server.host, port: context.server.port,
      username: 'EditorObserver', version: bot.version, auth: 'offline' })
    observer.on('messagestr', message => observerMessages.push(message))
    observer.on('error', error => rejectObserver(error))
    observer.on('kicked', reason => rejectObserver(new Error(`Observer kicked: ${JSON.stringify(reason)}`)))
    observer.on('end', reason => {
      if (!quitting) rejectObserver(new Error(`Observer disconnected: ${reason}`))
    })
    const run = async () => {
      await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('Observer did not spawn before the timeout')), timeout)
        observer.once('spawn', () => { clearTimeout(timer); resolve() })
      })
      await step('confirm a non-operator player and a live observer', async () => {
        await command('/minecraft:seed', /permission|Unknown or incomplete command|Unknown command/i)
        expect(observer.entity !== undefined && observer.health > 0, 'Observer did not remain active')
      })
      if (mode === 'deny') {
        await step('deny the inventory editor to a player with SELF language access', async () => {
          for (const [root, name] of [['sp', 'ShapedPortals'], ['biletools', 'BileTools']]) {
            await command(`/${root} language self en_US`, `${name}: your language is now en_US.`)
            const files = [shapedFrench, bileFrench]
            const before = await Promise.all(files.map(snapshot))
            await command(`/${root} language server edit fr_FR`, /permission/i)
            await context.sleep(150)
            expect(bot.currentWindow === null, 'SELF-only permission opened an editor', name)
            for (let index = 0; index < files.length; index++) {
              await unchanged(files[index], before[index], 'Denied editor access changed a language file')
            }
          }
        })
        return
      }
      if (mode === 'reload') {
        await step('retain edits and language choices after reloading ShapedPortals', async () => {
          await command('/biletools reload ShapedPortals', /Reloaded ShapedPortals|Rechargé : ShapedPortals/)
          const deadline = Date.now() + timeout
          let hydrated = false
          while (Date.now() < deadline) {
            const response = await command('/sp status', /ShapedPortals runtime|Editor Shaped French/)
            if (response.includes('Editor Shaped French')) { hydrated = true; break }
          }
          expect(hydrated, 'Persisted personal language did not hydrate after reloading ShapedPortals')
          await open('sp', 'ShapedPortals', 'fr_FR')
          await search('Editor Shaped French', 'command.status.header')
          expect(lore(bot.currentWindow.slots[0]).includes('Editor Shaped French'), 'ShapedPortals reload lost its edited message')
          await close()
        })
        await step('retain native overrides and the shared editor after reloading BileTools', async () => {
          const before = (await readFile(context.server.logPath, 'utf8')).length
          await command('/biletools reload BileTools', /Reloading BileTools|Rechargement en cours : BileTools/)
          const deadline = Date.now() + timeout
          let enabled = false
          while (Date.now() < deadline) {
            const log = (await readFile(context.server.logPath, 'utf8')).slice(before)
            if (log.includes('Enabling BileTools') && log.includes('Runtime platform: FOLIA')) { enabled = true; break }
            await context.sleep(100)
          }
          expect(enabled, 'BileTools did not complete its targeted reload')
          await open('biletools', 'BileTools', 'fr_FR')
          await search('bile.message.plugin_not_found', 'bile.message.plugin_not_found')
          expect(lore(bot.currentWindow.slots[0]).includes('Editor Bile second'), 'BileTools reload lost its multiline message')
          await close()
          const hydrationDeadline = Date.now() + timeout
          let hydrated = false
          while (Date.now() < hydrationDeadline) {
            const response = await command('/biletools reload MissingEditorPlugin', /Couldn't find|Editor Bile missing|Plugin introuvable/)
            if (response.includes('Editor Bile missing MissingEditorPlugin')) { hydrated = true; break }
          }
          expect(hydrated, 'Persisted personal language did not hydrate after reloading BileTools')
          for (const root of ['sp', 'biletools']) {
            await command(`/${root} language server`, 'Current: en_US')
            await command(`/${root} language self`, 'Current: fr_FR')
          }
        })
        return
      }
      if (mode === 'expiry') {
        await step('expire a private edit without modifying the language file', async () => {
          await open('sp', 'ShapedPortals', 'fr_FR')
          await search('command.status.header', 'command.status.header')
          const before = await snapshot(shapedFrench)
          await prompt(0, 'Edit fr_FR: command.status.header')
          await context.waitForMessage('Language editor input expired.', 70000)
          await ready('ShapedPortals - fr_FR')
          await unchanged(shapedFrench, before, 'Expired editor input modified its language file')
        })
        return
      }
      await step('prepare distinct server and personal choices', async () => {
        for (const [root, name] of [['sp', 'ShapedPortals'], ['biletools', 'BileTools']]) {
          await command(`/${root} language server en_US`, `${name}: server language is now en_US.`)
          await command(`/${root} language self fr_FR`, `${name}: your language is now fr_FR.`)
        }
      })
      const selections = await Promise.all(selectionFiles.map(snapshot))
      const initialInventory = inventory()
      const englishFiles = [path.join(plugins, 'ShapedPortals/languages/en_US.toml'),
        path.join(plugins, 'BileTools/languages/overrides/en_US.yml')]
      const englishBefore = await Promise.all(englishFiles.map(snapshot))
      await step('open the locale editor through server picker links and ShapedPortals config', async () => {
        for (const [root, name] of [['sp', 'ShapedPortals'], ['biletools', 'BileTools']]) {
          const packets = []
          const collect = message => packets.push(message.json)
          bot.on('message', collect)
          try {
            await command(`/${root} language server`, 'Current: en_US')
            await context.sleep(200)
          } finally {
            bot.removeListener('message', collect)
          }
          const actions = packets.flatMap(commandValues)
          expect(actions.includes(`/${root} language server edit`), 'Server picker has no editor action', { root, actions })
          expect(actions.includes(`/${root} language server edit fr_FR`), 'Server picker has no per-locale editor action', { root, actions })
          await open(root, name)
          await close()
        }
        await command('/sp config')
        await until(() => bot.currentWindow?.slots[34]?.name === 'bookshelf', 'ShapedPortals config did not show its Languages tile')
        await click(34)
        await ready('ShapedPortals - Language editor')
      })
      await step('paginate local language files and the selected locale message list', async () => {
        expect(itemName(bot.currentWindow.slots[51]) === 'Next page', 'Locale list did not paginate its local fixtures')
        const first = itemName(bot.currentWindow.slots[0])
        const previous = await click(51)
        await newWindow(previous)
        expect(itemName(bot.currentWindow.slots[47]) === 'Previous page' && itemName(bot.currentWindow.slots[0]) !== first,
          'Locale next page did not change the visible entries')
        const second = await click(47)
        await newWindow(second)
        expect(itemName(bot.currentWindow.slots[0]) === first, 'Locale previous page did not restore its first entry')
        const french = bot.currentWindow.slots.slice(0, 45).findIndex(item => itemName(item).startsWith('fr_FR -'))
        expect(french >= 0, 'French locale was not available in the locale picker')
        await click(french)
        await ready('ShapedPortals - fr_FR')
        expect(itemName(bot.currentWindow.slots[51]) === 'Next page', 'Message list did not paginate')
        const firstMessage = itemName(bot.currentWindow.slots[0])
        const page = await click(51)
        await newWindow(page)
        expect(itemName(bot.currentWindow.slots[0]) !== firstMessage && lore(bot.currentWindow.slots[49]).includes('Page 2'),
          'Message next page did not update its entries and page indicator')
        const back = await click(47)
        await newWindow(back)
      })
      await step('save a ShapedPortals message in its native file and render it for the selected player locale', async () => {
        await search('command.status.header', 'command.status.header')
        await prompt(0, 'Edit fr_FR: command.status.header')
        await save('{prefix}Editor Shaped French')
        expect((await readFile(shapedFrench, 'utf8')).includes('Editor Shaped French'), 'ShapedPortals did not persist its edited template')
        expect(lore(bot.currentWindow.slots[0]).includes('Editor Shaped French'), 'Saved message did not refresh in its locale')
        await close()
        await command('/sp status', 'Editor Shaped French')
        await open('sp', 'ShapedPortals', 'fr_FR')
        await search('Editor Shaped French', 'command.status.header')
        const refreshed = await click(49)
        await newWindow(refreshed)
        expect(title() === 'ShapedPortals - fr_FR' && lore(bot.currentWindow.slots[0]).includes('Editor Shaped French'),
          'Refresh did not preserve the edited locale and message search')
      })
      await step('reject invalid placeholders and cancelled edits without writing files or transferring menu items', async () => {
        const before = await snapshot(shapedFrench)
        await prompt(0, 'Edit fr_FR: command.status.header')
        const rejected = context.waitForMessage(/Unable to (edit|save):/, timeout)
        await chatInput('{prefix}Editor invalid {bad_editor_variable}')
        await rejected
        await ready('ShapedPortals - fr_FR')
        await unchanged(shapedFrench, before, 'Invalid placeholders modified the language file')
        await prompt(0, 'Edit fr_FR: command.status.header', 1)
        await chatInput('cancel')
        await ready('ShapedPortals - fr_FR')
        await unchanged(shapedFrench, before, 'Cancelled editor input modified the language file')
        await close()
        expect(inventory() === initialInventory && bot.inventory.selectedItem == null,
          'Menu click or shift-click transferred an editor item into the player inventory')
      })
      await step('recheck administrator permission when private input is submitted', async () => {
        await open('sp', 'ShapedPortals', 'fr_FR')
        await search('command.status.header', 'command.status.header')
        const before = await snapshot(shapedFrench)
        await prompt(0, 'Edit fr_FR: command.status.header')
        await command('/permissionfixture revoke', 'Editor permissions revoked.')
        const denied = context.waitForMessage('You do not have permission to edit ShapedPortals languages.', timeout)
        await chatInput('{prefix}Editor revoked must not persist')
        await denied
        await context.sleep(150)
        expect(bot.currentWindow === null, 'Revoked permission reopened the editor')
        await unchanged(shapedFrench, before, 'Revoked editor permission allowed a file write')
        await command('/permissionfixture restore', 'Editor permissions restored.')
      })
      await step('save and render a multiline BileTools text override', async () => {
        await open('biletools', 'BileTools', 'fr_FR')
        await search('bile.message.plugin_not_found', 'bile.message.plugin_not_found')
        await prompt(0, 'Edit fr_FR: bile.message.plugin_not_found')
        await save('Editor Bile missing {plugin}\\nEditor Bile second')
        const saved = await readFile(bileFrench, 'utf8')
        expect(saved.includes('Editor Bile missing {plugin}') && saved.includes('Editor Bile second'),
          'BileTools did not persist its multiline native override')
        expect(lore(bot.currentWindow.slots[0]).includes('Editor Bile second'), 'Multiline preview did not show its second line')
        await close()
        const secondLine = context.waitForMessage('Editor Bile second', timeout)
        await command('/biletools reload MissingEditorPlugin', 'Editor Bile missing MissingEditorPlugin')
        await secondLine
      })
      await step('edit one defined plural form while preserving the other form', async () => {
        await open('biletools', 'BileTools', 'fr_FR')
        await search('bile.message.remote.deployed', 'bile.message.remote.deployed')
        const previous = await click(0)
        await newWindow(previous)
        const entries = bot.currentWindow.slots.slice(0, 45).filter(Boolean)
        expect(entries.map(itemName).join(',') === 'one,other', 'Plural submenu did not expose only its defined forms', entries.map(itemName))
        const otherBefore = lore(entries[1])
        await prompt(0, 'Edit fr_FR: bile.message.remote.deployed [one]')
        await save('Editor plural {plugin} {count}')
        expect(itemName(bot.currentWindow.slots[0]) === 'one' && lore(bot.currentWindow.slots[0]).includes('Editor plural'),
          'Saved plural form did not refresh its submenu')
        expect(lore(bot.currentWindow.slots[1]) === otherBefore, 'Saving one plural form changed another form')
        const persisted = await readFile(bileFrench, 'utf8')
        expect(persisted.includes('Editor plural {plugin} {count}'), 'BileTools did not persist its edited plural form')
        const refreshed = await click(49)
        await newWindow(refreshed)
        expect(lore(bot.currentWindow.slots[0]).includes('Editor plural') && lore(bot.currentWindow.slots[1]) === otherBefore,
          'Refresh lost a plural edit or modified another form')
        const back = await click(45)
        await newWindow(back)
        const clear = await click(50)
        await newWindow(clear)
        expect(itemName(bot.currentWindow.slots[50]) !== 'Clear search' && itemName(bot.currentWindow.slots[51]) === 'Next page',
          'Clear search did not restore the complete message list')
        await click(53)
        await until(() => bot.currentWindow === null, 'Close control left the editor open')
      })
      await step('preserve other locales, server defaults, and personal overrides throughout editing', async () => {
        for (let index = 0; index < englishFiles.length; index++) {
          await unchanged(englishFiles[index], englishBefore[index], 'French editing modified an English language file')
        }
        for (let index = 0; index < selectionFiles.length; index++) {
          await unchanged(selectionFiles[index], selections[index], 'Editing changed a language selection file')
        }
        for (const root of ['sp', 'biletools']) {
          await command(`/${root} language server`, 'Current: en_US')
          await command(`/${root} language self`, 'Current: fr_FR')
        }
        expect(inventory() === initialInventory && bot.inventory.selectedItem == null, 'Editor navigation transferred menu items')
      })
    }
    try {
      await Promise.race([run(), observerFailure])
      await step('keep editor input private from another connected player', async () => {
        await context.sleep(250)
        const leaked = observerMessages.filter(message => [...privateInputs].some(input => message.includes(input))
          || /Search message keys or text|Enter text in chat;|Edit fr_FR:|Saved fr_FR\./.test(message))
        expect(leaked.length === 0, 'Private editor input or prompts reached another player', leaked)
        expect(bot.entity !== undefined && bot.health > 0 && observer.entity !== undefined && observer.health > 0,
          'A player did not remain active during editor checks')
      })
    } finally {
      quitting = true
      await close()
      observer.quit('Language editor scenario completed')
    }
  }
}
