package VLADMIX90.motorcyclePlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MotorcycleManagerTest {
    @Test
    void hidesChatNotificationsFromPlayersWithoutAdminPermission() throws Exception {
        MotorcyclePlugin plugin = mock(MotorcyclePlugin.class);
        when(plugin.message("messages.already-riding")).thenReturn("<red>Already riding");
        when(plugin.miniMessage()).thenReturn(MiniMessage.miniMessage());
        MotorcycleManager manager = new MotorcycleManager(plugin);
        Player player = mock(Player.class);
        when(player.hasPermission("motorcycle.admin")).thenReturn(false);

        sendNotification(manager, player);

        verify(player, never()).sendMessage(Component.text("Already riding", net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    @Test
    void keepsChatNotificationsForPlayersWithAdminPermission() throws Exception {
        MotorcyclePlugin plugin = mock(MotorcyclePlugin.class);
        when(plugin.message("messages.already-riding")).thenReturn("<red>Already riding");
        when(plugin.miniMessage()).thenReturn(MiniMessage.miniMessage());
        MotorcycleManager manager = new MotorcycleManager(plugin);
        Player player = mock(Player.class);
        when(player.hasPermission("motorcycle.admin")).thenReturn(true);

        sendNotification(manager, player);

        verify(player).sendMessage(Component.text("Already riding", net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    private static void sendNotification(MotorcycleManager manager, Player player) throws Exception {
        Method send = MotorcycleManager.class.getDeclaredMethod("send", Player.class, String.class);
        send.setAccessible(true);
        send.invoke(manager, player, "messages.already-riding");
    }
}
