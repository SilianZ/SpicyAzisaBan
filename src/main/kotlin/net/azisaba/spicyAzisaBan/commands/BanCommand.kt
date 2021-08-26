package net.azisaba.spicyAzisaBan.commands

import net.azisaba.spicyAzisaBan.SABConfig
import net.azisaba.spicyAzisaBan.SABMessages
import net.azisaba.spicyAzisaBan.SABMessages.replaceVariables
import net.azisaba.spicyAzisaBan.SpicyAzisaBan
import net.azisaba.spicyAzisaBan.punishment.Punishment
import net.azisaba.spicyAzisaBan.punishment.PunishmentType
import net.azisaba.spicyAzisaBan.util.Util.broadcastMessageAfterRandomTime
import net.azisaba.spicyAzisaBan.util.Util.connectToLobbyOrKick
import net.azisaba.spicyAzisaBan.util.Util.filterArgKeys
import net.azisaba.spicyAzisaBan.util.Util.filtr
import net.azisaba.spicyAzisaBan.util.Util.getServerName
import net.azisaba.spicyAzisaBan.util.Util.getUniqueId
import net.azisaba.spicyAzisaBan.util.Util.kick
import net.azisaba.spicyAzisaBan.util.Util.send
import net.azisaba.spicyAzisaBan.util.Util.sendErrorMessage
import net.azisaba.spicyAzisaBan.util.Util.translate
import net.azisaba.spicyAzisaBan.util.contexts.Contexts
import net.azisaba.spicyAzisaBan.util.contexts.PlayerContext
import net.azisaba.spicyAzisaBan.util.contexts.ReasonContext
import net.azisaba.spicyAzisaBan.util.contexts.ServerContext
import net.azisaba.spicyAzisaBan.util.contexts.get
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.TabExecutor
import util.ArgumentParser
import util.kt.promise.rewrite.catch
import util.promise.rewrite.Promise

object BanCommand: Command("${SABConfig.prefix}ban"), TabExecutor {
    private val availableArguments = listOf("player=", "reason=\"\"", "server=", "--all")

    override fun execute(sender: CommandSender, args: Array<String>) {
        if (sender !is ProxiedPlayer) return sender.send("${ChatColor.RED}This command cannot be used from console!")
        if (!sender.hasPermission(PunishmentType.BAN.perm)) {
            return sender.send(SABMessages.General.missingPermissions.replaceVariables().translate())
        }
        if (args.isEmpty()) return sender.send(SABMessages.Commands.Ban.usage.replaceVariables().translate())
        val arguments = ArgumentParser(args.joinToString(" "))
        Promise.create<Unit> { context ->
            if (!arguments.containsKey("server")) {
                val serverName = sender.server.info.name
                val group = SpicyAzisaBan.instance.connection.getGroupByServer(serverName).complete()
                arguments.parsedOptions["server"] = group ?: serverName
            }
            doBan(sender, arguments)
            context.resolve()
        }.catch {
            sender.sendErrorMessage(it)
        }
    }

    internal fun doBan(sender: CommandSender, arguments: ArgumentParser) {
        val player = arguments.get(Contexts.PLAYER, sender).complete().apply { if (!isSuccess) return }
        val server = arguments.get(Contexts.SERVER, sender).complete().apply { if (!isSuccess) return }
        val reason = arguments.get(Contexts.REASON, sender).complete()
        if (Punishment.canJoinServer(player.profile.uniqueId, null, server.name).complete() != null) {
            sender.send(SABMessages.Commands.General.alreadyPunished.replaceVariables().translate())
            return
        }
        val p = Punishment
            .createByPlayer(player.profile, reason.text, sender.getUniqueId(), PunishmentType.BAN, -1, server.name)
            .insert()
            .thenDo {
                ProxyServer.getInstance().getPlayer(player.profile.uniqueId)?.apply {
                    connectToLobbyOrKick(server, TextComponent.fromLegacyText(it.getBannedMessage().complete()))
                    ProxyServer.getInstance().getServerInfo(server.name)?.broadcastMessageAfterRandomTime(server.name)
                }
            }
            .catch {
                SpicyAzisaBan.instance.logger.warning("Something went wrong while handling command from ${sender.name}!")
                sender.sendErrorMessage(it)
            }
            .complete() ?: return
        p.notifyToAll().complete()
        if (arguments.contains("all")) {
            p.applyToSameIPs(player.profile.uniqueId).catch { sender.sendErrorMessage(it) }.complete()
        }
        sender.send(SABMessages.Commands.Ban.done.replaceVariables(p.getVariables().complete()).translate())
    }

    override fun onTabComplete(sender: CommandSender, args: Array<String>): Iterable<String> {
        if (!sender.hasPermission(PunishmentType.BAN.perm)) return emptyList()
        if (args.isEmpty()) return emptyList()
        val s = args.last()
        if (!s.contains("=")) return availableArguments.filterArgKeys(args).filtr(s)
        if (s.startsWith("player=")) return PlayerContext.tabComplete(s)
        if (s.startsWith("server=")) return ServerContext.tabComplete(s)
        if (s.startsWith("reason=")) return ReasonContext.tabComplete(PunishmentType.BAN, args, sender.getServerName())
        return emptyList()
    }
}
