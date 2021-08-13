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
import net.azisaba.spicyAzisaBan.util.contexts.ServerContext
import net.azisaba.spicyAzisaBan.util.contexts.TimeContext
import net.azisaba.spicyAzisaBan.util.contexts.get
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.TabExecutor
import util.ArgumentParser
import util.kt.promise.rewrite.catch
import util.promise.rewrite.Promise

object GlobalTempBanCommand: Command("gtempban"), TabExecutor {
    private val availableArguments = listOf("player=", "reason=\"\"", "server=", "time=")

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (!sender.hasPermission(PunishmentType.TEMP_BAN.perm)) {
            return sender.send(SABMessages.General.missingPermissions.replaceVariables().translate())
        }
        if (args.isEmpty()) return sender.send(SABMessages.Commands.TempBan.globalUsage.replaceVariables().translate())
        val arguments = ArgumentParser(args.joinToString(" "))
        Promise.create<Unit> { context ->
            val player = arguments.get(Contexts.PLAYER, sender).complete().apply { if (!isSuccess) return@create context.resolve() }
            val server = arguments.get(Contexts.SERVER, sender).complete().apply { if (!isSuccess) return@create context.resolve() }
            val reason = arguments.get(Contexts.REASON, sender).complete()
            val time = arguments.get(Contexts.TIME, sender).complete().apply { if (!isSuccess) return@create context.resolve() }
            if (time.time == -1L) {
                sender.send(SABMessages.Commands.General.timeNotSpecified.replaceVariables().translate())
                return@create context.resolve()
            }
            val p = Punishment(
                -1,
                player.profile.name,
                player.profile.uniqueId.toString(),
                reason.text,
                sender.getUniqueId(),
                PunishmentType.TEMP_BAN,
                System.currentTimeMillis(),
                System.currentTimeMillis() + time.time,
                server.name,
            )
                .insert()
                .thenDo {
                    ProxyServer.getInstance().getPlayer(player.profile.uniqueId)?.kick(SABMessages.Commands.TempBan.layout.replaceVariables(it.getVariables().complete()).translate())
                }
                .catch {
                    sender.send(SABMessages.General.error.replaceVariables().translate())
                    SpicyAzisaBan.instance.logger.warning("Something went wrong while handling /gtempban from ${sender.name}!")
                    it.printStackTrace()
                }
                .complete() ?: return@create context.resolve()
            p.notifyToAll().complete()
            sender.send(SABMessages.Commands.TempBan.done.replaceVariables(p.getVariables().complete()).translate())
            context.resolve()
        }
    }

    override fun onTabComplete(sender: CommandSender, args: Array<String>): Iterable<String> {
        if (args.isEmpty()) return emptyList()
        val s = args.last()
        if (!s.contains("=")) return availableArguments.filterArgKeys(args).filtr(s)
        if (s.startsWith("player=")) return PlayerContext.tabComplete(s)
        if (s.startsWith("server=")) return ServerContext.tabComplete(s)
        if (s.startsWith("time=")) return TimeContext.tabComplete(s)
        return emptyList()
    }
}
