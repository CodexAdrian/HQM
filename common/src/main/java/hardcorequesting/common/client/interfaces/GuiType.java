package hardcorequesting.common.client.interfaces;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import hardcorequesting.common.network.IMessage;
import hardcorequesting.common.network.message.OpenGuiMessage;
import hardcorequesting.common.tileentity.TrackerBlockEntity;
import hardcorequesting.common.tileentity.TrackerType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.io.StringWriter;
import java.util.UUID;

public enum GuiType {
    NONE {
        @Override
        public IMessage build(String... data) {
            return null;
        }
        
        @Override
        public void open(Player player, String data) {
            
        }
    },
    TRACKER {
        private static final String BLOCK_POS = "blockPos";
        private static final String QUEST = "quest";
        private static final String RADIUS = "radius";
        private static final String TYPE = "trackerType";
        
        
        @Override
        public IMessage build(String... data) {
            StringWriter stringWriter = new StringWriter();
            try {
                JsonWriter writer = new JsonWriter(stringWriter);
                writer.beginObject();
                writer.name(BLOCK_POS).value(data[0]);
                writer.name(QUEST).value(data[1]);
                writer.name(RADIUS).value(data[2]);
                writer.name(TYPE).value(data[3]);
                writer.endObject();
            } catch (IOException ignored) {
            }
            
            return new OpenGuiMessage(this, stringWriter.toString());
        }
        
        @Override
        public void open(Player player, String data) {
            JsonParser parser = new JsonParser();
            JsonObject root = parser.parse(data).getAsJsonObject();
            BlockPos pos = BlockPos.of(root.get(BLOCK_POS).getAsLong());
            JsonElement quest = root.get(QUEST);
            UUID questId = quest.isJsonNull() ? null : UUID.fromString(root.getAsString());
            int radius = root.get(RADIUS).getAsInt();
            TrackerType type = TrackerType.values()[root.get(TYPE).getAsInt()];
            TrackerBlockEntity.openInterface(player, pos, questId, radius, type);
        }
    },
    BOOK {
        @Override
        public IMessage build(String... data) {
            return new OpenGuiMessage(this, data[0]);
        }
        
        @Override
        public void open(Player player, String data) {
            GuiQuestBook.displayGui(player, Boolean.parseBoolean(data));
        }
    };
    
    public abstract IMessage build(String... data);
    
    public abstract void open(Player player, String data);
}
