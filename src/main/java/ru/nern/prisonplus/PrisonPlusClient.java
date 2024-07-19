package ru.nern.prisonplus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class PrisonPlusClient implements ClientModInitializer {

    public static int ticksLeft;
    public static boolean isIRL;

    @Override
    public void onInitializeClient() {
        PrisonNetworking.initClient();
        //Очищает список подписанных тюрем при выходе клиента с сервера
        ClientPlayConnectionEvents.DISCONNECT.register(((handler, client) -> PrisonPlus.RENDERED_PRISONS.clear()));

        //Тикаем оставшееся время на клиенте
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if((!client.isPaused() || isIRL) && ticksLeft > 0) ticksLeft--;
        });
    }
}
