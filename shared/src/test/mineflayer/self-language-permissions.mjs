export default {
  name: 'self-language-permissions',
  description: 'Validate personal language permissions across BileTools and ShapedPortals for non-operators.',
  async run(context) {
    const { bot, expect, step, waitForMessage } = context
    const timeout = 15000
    const mode = context.options.command ?? 'default-allow'
    expect(['default-allow', 'deny-plugin', 'deny-shared', 'server-allowed'].includes(mode),
      'Unknown personal language permission scenario', mode)

    const command = async (text, expected) => {
      await context.sleep(1100)
      return context.command(text, expected, timeout)
    }
    const serverCommand = async (text, expected) => {
      await context.sleep(1100)
      const responses = ['BileTools', 'ShapedPortals'].map(name =>
        waitForMessage(`${name}: ${expected}`, timeout))
      bot.chat(text)
      await Promise.all(responses)
    }
    const denied = async text => {
      const messages = []
      const collect = message => messages.push(message)
      bot.on('messagestr', collect)
      try {
        await command(text, 'You do not have permission')
        await context.sleep(250)
        expect(!messages.some(message => /Preparing language|language is now/.test(message)),
          'A rejected language command started or completed a language change', { text, messages })
      } finally {
        bot.removeListener('messagestr', collect)
      }
    }

    await step('confirm the player has no operator command access', async () => {
      await command('/minecraft:seed', /permission|Unknown or incomplete command|Unknown command/i)
    })

    if (mode === 'default-allow') {
      await step('allow personal selection and reset by default for each provider', async () => {
        for (const [root, name] of [['sp', 'ShapedPortals'], ['biletools', 'BileTools']]) {
          await command(`/${root} language self`, 'Current: en_US')
          await command(`/${root} language self en_US`, `${name}: your language is now en_US.`)
          await command(`/${root} language self reset`, `${name}: your language is now the server default.`)
        }
      })
      await step('keep server selection protected while personal selection is allowed', async () => {
        await denied('/sp language server en_US')
        await denied('/biletools language server en_US')
        await denied('/volmit plugins languages en_US')
      })
    } else if (mode === 'deny-plugin') {
      await step('deny personal selection and reset for the restricted provider', async () => {
        await denied('/sp language')
        await denied('/sp language self en_US')
        await denied('/sp language self reset')
      })
      await step('allow the other provider independently', async () => {
        await command('/biletools language self en_US', 'BileTools: your language is now en_US.')
        await command('/biletools language self', 'Current: en_US')
      })
      await step('keep personal preferences separate between providers', async () => {
        await denied('/sp language self reset')
        await command('/biletools language self', 'Current: en_US')
      })
      await step('keep the allowed provider reset available and server selection protected', async () => {
        await command('/biletools language self reset', 'BileTools: your language is now the server default.')
        await denied('/sp language server en_US')
        await denied('/biletools language server en_US')
      })
    } else if (mode === 'deny-shared') {
      await step('honor the shared personal permission for each provider', async () => {
        for (const root of ['sp', 'biletools']) {
          await denied(`/${root} language self en_US`)
          await denied(`/${root} language self reset`)
        }
      })
      await step('keep server selection protected when shared personal access is denied', async () => {
        await denied('/sp language server en_US')
        await denied('/biletools language server en_US')
      })
    } else {
      await step('deny personal access even when server language access is granted', async () => {
        await denied('/sp language self en_US')
        await denied('/sp language self reset')
        await denied('/biletools language self en_US')
      })
      await step('allow the separate server permission despite personal permission denial', async () => {
        await command('/sp language server', 'Current: en_US')
        await command('/sp language server en_US', 'ShapedPortals: server language is now en_US.')
        await serverCommand('/volmit plugins languages en_US', 'server language is now en_US.')
        await command('/biletools language server', 'Current: en_US')
      })
    }

    expect(bot.entity !== undefined && bot.health > 0,
      'Player did not remain active after personal language permission checks')
  }
}
