package ru.nern.prisonplus;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import ru.nern.prisonplus.structure.Prison;
import ru.nern.prisonplus.structure.PrisonCell;
import ru.nern.prisonplus.utils.IPlayerAccessor;
import ru.nern.prisonplus.utils.PrisonUtils;

public class PrisonNetworking {
    public static final Identifier SUBSCRIBE_PRISON_PACKET = new Identifier("prisonplus", "subscribe");
    public static final Identifier UNSUBSCRIBE_PRISON_PACKET = new Identifier("prisonplus", "unsubscribe");
    public static final Identifier PRISON_TRACK_PACKET = new Identifier("prisonplus", "track");
    public static final Identifier TIME_SYNC_PACKET_ID = new Identifier("prisonplus", "timesync");

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        //Пакет, отвечающий за подписку на рендер тюрьмы
        ClientPlayNetworking.registerGlobalReceiver(SUBSCRIBE_PRISON_PACKET, (client, handler, buf, responseSender) -> {
            String name = buf.readString();
            Identifier dimension = buf.readIdentifier();
            int[] cellBounds = buf.readIntArray();
            int[] boundaries = buf.readIntArray();


            client.execute(() -> {
                Prison prison = new Prison(name, PrisonUtils.toBlockBox(boundaries), RegistryKey.of(RegistryKeys.WORLD, dimension), true);
                prison.checkDimension(client.world.getDimensionKey().getValue());

                for(int i=0; i < cellBounds.length; i+=6) {
                    prison.addCell(prison.createCell("Client"+i, new BlockBox(cellBounds[i], cellBounds[i+1], cellBounds[i+2], cellBounds[i+3], cellBounds[i+4], cellBounds[i+5]), 0));
                }

                PrisonPlus.RENDERED_PRISONS.put(prison.getName(), prison);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(PRISON_TRACK_PACKET, (client, handler, buf, responseSender) -> {
            String name = buf.readString();
            int[] cellBounds = buf.readIntArray();
            int[] boundaries = buf.readIntArray();

            client.execute(() -> {
                Prison prison = PrisonPlus.RENDERED_PRISONS.get(name);
                prison.setPrisonBoundaries(PrisonUtils.toBlockBox(boundaries));

                //Если количество клеток изменилось, то пересоздаём их все
                if(cellBounds.length != prison.getCellAmount()*6) {
                    prison.removeAllCells();
                    for(int i=0; i<cellBounds.length; i += 6) {
                        prison.addCell(prison.createCell("Client"+i, new BlockBox(cellBounds[i], cellBounds[i+1], cellBounds[i+2], cellBounds[i+3], cellBounds[i+4], cellBounds[i+5]), 0));
                    }
                }else{
                    //Меняем хитбоксы клеток
                    for(int i=0; i<cellBounds.length; i += 6) {
                        prison.getCells().get("Client"+i).setBoundaries(new BlockBox(cellBounds[i], cellBounds[i+1], cellBounds[i+2], cellBounds[i+3], cellBounds[i+4], cellBounds[i+5]));
                    }
                }

            });
        });
        //Отвечает за отписку от тюрьмы
        ClientPlayNetworking.registerGlobalReceiver(UNSUBSCRIBE_PRISON_PACKET, (client, handler, buf, responseSender) -> {
            String name = buf.readString();
            client.execute(() -> PrisonPlus.RENDERED_PRISONS.remove(name));
        });

        //Отвечает за синхронизацию клиенту количества времени, которое ему осталось в тюрьме
        ClientPlayNetworking.registerGlobalReceiver(TIME_SYNC_PACKET_ID, (client, handler, buf, responseSender) -> {
            PrisonPlusClient.isIRL = buf.readBoolean();
            PrisonPlusClient.ticksLeft = buf.readInt();
        });
    }

    public static PacketByteBuf serializePrisonToClientInitial(Prison prison, ServerWorld world) {
        PacketByteBuf buf = PacketByteBufs.create();
        //Записываем данные тюрьмы
        buf.writeString(prison.getName());
        //Записываем измерение
        buf.writeIdentifier(world.getDimensionKey().getValue());

        int[] cells = new int[prison.getCellAmount()*6];

        //Переводим боксы клеток в числовой массив
        int i = 0;
        for(PrisonCell cell : prison.getCells().values()) {
            int[] bounds = PrisonUtils.fromBlockBox(cell.getBounds());
            System.arraycopy(bounds, 0, cells, i, bounds.length);
            i+= 6;
        }
        buf.writeIntArray(cells);
        buf.writeIntArray(PrisonUtils.fromBlockBox(prison.getBounds()));

        return buf;
    }

    public static PacketByteBuf serializePrisonToClient(Prison prison, ServerWorld world) {
        PacketByteBuf buf = PacketByteBufs.create();
        //Записываем данные тюрьмы
        buf.writeString(prison.getName());
        int[] cellBounds = new int[prison.getCellAmount()*6];

        //Переводим боксы клеток в числовой массив
        int i = 0;
        for(PrisonCell cell : prison.getCells().values()) {
            int[] bounds = PrisonUtils.fromBlockBox(cell.getBounds());
            System.arraycopy(bounds, 0, cellBounds, i, bounds.length);
            i+= 6;
        }
        buf.writeIntArray(cellBounds);

        //Writing boundaries
        buf.writeIntArray(PrisonUtils.fromBlockBox(prison.getBounds()));

        return buf;
    }

    public static void subscribeToPrison(Prison prison, ServerWorld world, ServerPlayerEntity player) {
        PacketByteBuf buf = PrisonNetworking.serializePrisonToClientInitial(prison, world);
        ServerPlayNetworking.send(player, PrisonNetworking.SUBSCRIBE_PRISON_PACKET, buf);
        ((IPlayerAccessor)player).subscribe(prison.getName());
    }

    //unsubscribe нужен чтобы пофиксить ConcurrentModificationException в итераторе. Вызываем удаление именно в нём
    public static void unsubscribeFromPrison(String prisonName, ServerPlayerEntity player, boolean unsubscribe) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(prisonName);
        ServerPlayNetworking.send(player, PrisonNetworking.UNSUBSCRIBE_PRISON_PACKET, buf);
        if(unsubscribe) ((IPlayerAccessor)player).unsubscribe(prisonName);
    }

    public static void trackPrison(Prison prison, ServerWorld world, ServerPlayerEntity player) {
        PacketByteBuf buf = PrisonNetworking.serializePrisonToClient(prison, world);
        ServerPlayNetworking.send(player, PrisonNetworking.PRISON_TRACK_PACKET, buf);
    }

    public static boolean subscribedTo(String prisonName, ServerPlayerEntity player) {
        return ((IPlayerAccessor)player).subscribedTo(prisonName);
    }
}
