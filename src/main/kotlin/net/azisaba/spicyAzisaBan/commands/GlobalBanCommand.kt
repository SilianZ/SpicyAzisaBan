package net.azisaba.spicyAzisaBan.commands

import net.azisaba.spicyAzisaBan.SABMessages
import net.azisaba.spicyAzisaBan.SABMessages.replaceVariables
import net.azisaba.spicyAzisaBan.SpicyAzisaBan
import net.azisaba.spicyAzisaBan.punishment.Punishment
import net.azisaba.spicyAzisaBan.punishment.PunishmentType
import net.azisaba.spicyAzisaBan.util.Util.filterArgKeys
import net.azisaba.spicyAzisaBan.util.Util.filtr
import net.azisaba.spicyAzisaBan.util.Util.getUniqueId
import net.azisaba.spicyAzisaBan.util.Util.kick
import net.azisaba.spicyAzisaBan.util.Util.send
import net.azisaba.spicyAzisaBan.util.Util.translate
import net.azisaba.spicyAzisaBan.util.contexts.Contexts
import net.azisaba.spicyAzisaBan.util.contexts.PlayerContext
import net.azisaba.spicyAzisaBan.util.contexts.ReasonContext
import net.azisaba.spicyAzisaBan.util.contexts.ServerContext
import net.azisaba.spicyAzisaBan.util.contexts.get
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.TabExecutor
import util.ArgumentParser
import util.kt.promise.rewrite.catch
import util.promise.rewrite.Promise

object GlobalBanCommand: Command("gban"), TabExecutor {
    private val availableArguments = listOf("player=", "reason=\"\"", "server=")

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (!sender.hasPermission(PunishmentType.BAN.perm)) {
            return sender.send(SABMessages.General.missingPermissions.replaceVariables().translate())
        }
        if (args.isEmpty()) return sender.send(SABMessages.Commands.Ban.globalUsage.replaceVariables().translate())
        val arguments = ArgumentParser(args.joinToString(" "))
        Promise.create<Unit> { context ->
            val player = arguments.get(Contexts.PLAYER, sender).complete().apply { if (!isSuccess) return@create context.resolve() }
            val server = arguments.get(Contexts.SERVER, sender).complete().apply { if (!isSuccess) return@create context.resolve() }
            val reason = arguments.get(Contexts.REASON, sender).complete()
            val p = Punishment
                .createByPlayer(player.profile, reason.text, sender.getUniqueId(), PunishmentType.BAN, -1, server.name)
                .insert()
                .thenDo {
                    ProxyServer.getInstance().getPlayer(player.profile.uniqueId)?.kick(it.getBannedMessage().complete())
                }
                .catch {
                    sender.send(SABMessages.General.error.replaceVariables().translate())
                    SpicyAzisaBan.instance.logger.warning("Something went wrong while handling /gban from ${sender.name}!")
                    it.printStackTrace()
                }
                .complete() ?: return@create context.resolve()
            p.notifyToAll().complete()
            sender.send(SABMessages.Commands.Ban.done.replaceVariables(p.getVariables().complete()).translate())
            context.resolve()
        }.catch {
            it.printStackTrace()
            sender.send(SABMessages.General.error.replaceVariables().translate())
        }
    }

    override fun onTabComplete(sender: CommandSender, args: Array<String>): Iterable<String> {
        if (args.isEmpty()) return emptyList()
        val s = args.last()
        if (!s.contains("=")) return availableArguments.filterArgKeys(args).filtr(s)
        if (s.startsWith("player=")) return PlayerContext.tabComplete(s)
        if (s.startsWith("server=")) return ServerContext.tabComplete(s)
        if (s.startsWith("reason=")) return ReasonContext.tabComplete(PunishmentType.BAN, args, "global")
        return emptyList()
    }
}
