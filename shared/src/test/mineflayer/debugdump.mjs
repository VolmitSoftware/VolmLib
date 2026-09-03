import { readFile } from 'node:fs/promises'

function clickValues(value, action) {
  if (value === null || typeof value !== 'object') {
    return []
  }
  const values = value.action === action ? [value.value ?? value.text ?? value.url] : []
  for (const child of Object.values(value)) {
    values.push(...clickValues(child, action))
  }
  return values.filter(value => typeof value === 'string')
}

export default {
  name: 'debugdump',
  description: 'Validate diagnostic permissions, local report paths, upload links, and targeted plugin reloads.',
  async run(context) {
    const { bot, expect, step, waitForEvent, waitForMessage } = context
    const timeout = 30000
    const mode = context.options.command ?? 'dedicated'
    expect(['deny', 'dedicated', 'upload', 'reload'].includes(mode), 'Unknown diagnostic scenario', mode)
    const command = async (text, expected) => {
      await context.sleep(1100)
      return context.command(text, expected, timeout)
    }
    const dump = async (root, name, upload) => {
      await context.sleep(1100)
      const saved = waitForMessage(`Saved ${name} debug dump: debug/`, timeout)
      const copied = waitForEvent('message', message =>
        clickValues(message.json, 'copy_to_clipboard').some(value =>
          value.startsWith(`debug/${name.toLowerCase()}-debugdump-`)), timeout)
      const uploadResult = upload
        ? waitForMessage(/Debug dump: https:\/\/mclo\.gs\/|Debug dump upload (failed|was interrupted)/, timeout)
        : Promise.resolve(undefined)
      const uploaded = upload
        ? waitForEvent('message', message =>
          clickValues(message.json, 'open_url').some(value => /^https:\/\/mclo\.gs\/[A-Za-z0-9]+$/.test(value)), timeout)
        : Promise.resolve(undefined)
      const copiedLink = upload
        ? waitForEvent('message', message =>
          clickValues(message.json, 'copy_to_clipboard').some(value => /^https:\/\/mclo\.gs\/[A-Za-z0-9]+$/.test(value)), timeout)
        : Promise.resolve(undefined)
      bot.chat(`/${root} debugdump${upload ? '' : ' upload=false'}`)
      const [savedMessage, [copyMessage], outcome, openResult, copyResult] =
        await Promise.all([saved, copied, uploadResult, uploaded, copiedLink])
      const relativePath = savedMessage.match(/debug\/[a-z]+-debugdump-[A-Za-z0-9.-]+\.txt/)?.[0]
      expect(relativePath !== undefined, 'Diagnostic response did not contain a relative report path', savedMessage)
      expect(clickValues(copyMessage.json, 'copy_to_clipboard').includes(relativePath),
        'Copy path payload does not match the saved report path', copyMessage.json)
      expect(JSON.stringify(copyMessage.json).includes('[Copy path]'), 'Diagnostic path copy action has no label')
      if (upload) {
        expect(outcome.startsWith('Debug dump: https://mclo.gs/'), 'Diagnostic upload failed', outcome)
        const url = outcome.match(/https:\/\/mclo\.gs\/[A-Za-z0-9]+/)?.[0]
        expect(clickValues(openResult[0].json, 'open_url').includes(url), 'Diagnostic open URL differs from upload URL')
        expect(clickValues(copyResult[0].json, 'copy_to_clipboard').includes(url), 'Copy link payload differs from upload URL')
        expect(JSON.stringify(copyResult[0].json).includes('[Copy link]'), 'Diagnostic link copy action has no label')
      }
    }

    await step('confirm diagnostic access does not require operator status', async () => {
      await command('/minecraft:seed', /permission|Unknown or incomplete command|Unknown command/i)
    })
    if (mode === 'deny') {
      await step('deny diagnostic creation without each dedicated permission', async () => {
        await command('/sp debugdump upload=false', /permission/i)
        await command('/biletools debugdump upload=false', /permission/i)
      })
    } else if (mode === 'dedicated') {
      await step('keep root administration unavailable with dedicated diagnostic grants', async () => {
        await command('/sp status', /permission/i)
        await command('/biletools reload ShapedPortals', /permission/i)
      })
      await step('save ShapedPortals diagnostics and deliver the relative clipboard path', async () => {
        await dump('sp', 'ShapedPortals', false)
      })
      await step('save BileTools diagnostics and deliver the relative clipboard path', async () => {
        await dump('biletools', 'BileTools', false)
      })
    } else if (mode === 'upload') {
      await step('upload ShapedPortals diagnostics by default with matching open and copy actions', async () => {
        await dump('sp', 'ShapedPortals', true)
      })
      await step('upload BileTools diagnostics by default with matching open and copy actions', async () => {
        await dump('biletools', 'BileTools', true)
      })
    } else {
      await step('reload ShapedPortals and create a new diagnostic report', async () => {
        await command('/biletools reload ShapedPortals', /Reloaded ShapedPortals/)
        await dump('sp', 'ShapedPortals', false)
      })
      await step('reload BileTools and create a new diagnostic report', async () => {
        const before = (await readFile(context.server.logPath, 'utf8')).length
        await command('/biletools reload BileTools', /Reloading BileTools/)
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
        await dump('biletools', 'BileTools', false)
      })
    }
    expect(bot.entity !== undefined && bot.health > 0, 'Player did not remain active after diagnostic checks')
  }
}
