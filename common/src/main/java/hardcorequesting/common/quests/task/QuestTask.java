package hardcorequesting.common.quests.task;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import hardcorequesting.common.client.ClientChange;
import hardcorequesting.common.client.sounds.Sounds;
import hardcorequesting.common.event.EventTrigger;
import hardcorequesting.common.io.adapter.Adapter;
import hardcorequesting.common.io.adapter.QuestTaskAdapter;
import hardcorequesting.common.network.NetworkManager;
import hardcorequesting.common.quests.Quest;
import hardcorequesting.common.quests.QuestingData;
import hardcorequesting.common.quests.QuestingDataManager;
import hardcorequesting.common.quests.RepeatType;
import hardcorequesting.common.quests.data.QuestData;
import hardcorequesting.common.quests.data.TaskData;
import hardcorequesting.common.reputation.Reputation;
import hardcorequesting.common.reputation.ReputationMarker;
import hardcorequesting.common.team.RewardSetting;
import hardcorequesting.common.team.Team;
import hardcorequesting.common.team.TeamLiteStat;
import hardcorequesting.common.util.Translator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.UUID;

public abstract class QuestTask<Data extends TaskData> {
    
    private final TaskType<?> type;
    private final Class<Data> dataType;
    public String description;
    protected Quest parent;
    private String longDescription;
    private int id;
    
    public QuestTask(TaskType<?> type, Class<Data> dataType, Quest parent) {
        this.type = type;
        this.dataType = dataType;
        this.parent = parent;
        this.description = type.getLangKeyName();
        this.longDescription = type.getLangKeyDescription();
    }
    
    public TaskType<?> getType() {
        return type;
    }
    
    public static void completeQuest(Quest quest, UUID uuid) {
        if (!quest.isEnabled(uuid) || !quest.isAvailable(uuid)) return;
        for (QuestTask<?> questTask : quest.getTasks()) {
            if (!questTask.getData(uuid).completed) {
                return;
            }
        }
        QuestData data = quest.getQuestData(uuid);
        
        data.completed = true;
        data.teamRewardClaimed = false;
        data.available = false;
        data.time = Quest.serverTicker.getHours();
        
        
        if (QuestingDataManager.getInstance().getQuestingData(uuid).getTeam().getRewardSetting() == RewardSetting.RANDOM) {
            data.unlockRewardForRandom();
        } else {
           data.unlockRewardForAll();
        }
        quest.sendUpdatedDataToTeam(uuid);
        TeamLiteStat.refreshTeam(QuestingDataManager.getInstance().getQuestingData(uuid).getTeam());
        
        for (Quest child : quest.getReversedRequirement()) {
            completeQuest(child, uuid);
            child.sendUpdatedDataToTeam(uuid);
        }
        
        if (quest.getRepeatInfo().getType() == RepeatType.INSTANT) {
            quest.reset(uuid);
        }
        
        Player player = QuestingData.getPlayer(uuid);
        if (player instanceof ServerPlayer && !quest.hasReward(uuid)) {
            // when there is no reward and it just completes the quest play the music
            NetworkManager.sendToPlayer(ClientChange.SOUND.build(Sounds.COMPLETE), (ServerPlayer) player);
        }
        
        if (player != null) {
            EventTrigger.instance().onQuestComplete(new EventTrigger.QuestCompletedEvent(player, quest.getQuestId()));
        }
    }
    
    public abstract void write(Adapter.JsonObjectBuilder builder);
    
    public abstract void read(JsonObject object);
    
    public void updateId(int id) {
        this.id = id;
    }
    
    public boolean isCompleted(Player player) {
        return getData(player).completed;
    }
    
    public boolean isCompleted(UUID uuid) {
        return getData(uuid).completed;
    }
    
    public boolean isVisible(UUID playerId) {
        if (id > 0) {
            QuestTask<?> requirement = parent.getTasks().get(id - 1);
            return requirement.isCompleted(playerId) && requirement.isVisible(playerId);
        }
        else return true;
    }
    
    public void write(TaskData task, JsonObject out) {
        task.write(new Adapter.JsonObjectBuilder(out));
    }
    
    public void read(TaskData task, JsonReader in) throws IOException {
        task.update(QuestTaskAdapter.QUEST_DATA_TASK_ADAPTER.read(in));
    }
    
    protected Data getData(Player player) {
        return getData(player.getUUID());
    }
    
    protected Data getData(UUID uuid) {
        return getData(parent.getQuestData(uuid));
    }
    
    protected Data getData(Team team) {
        return getData(team.getQuestData(parent.getQuestId()));
    }
    
    private Data getData(QuestData questData) {
        if (this.id < 0) {
            return newQuestData(); // possible fix for #247
        }
        
        return questData.getTaskData(parent, id, dataType, this::newQuestData);
    }
    
    public abstract Data newQuestData();
    
    public String getLangKeyDescription() {
        return description;
    }
    
    public MutableComponent getDescription() {
        return Translator.translatable(description);
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getLangKeyLongDescription() {
        return longDescription;
    }
    
    public FormattedText getLongDescription() {
        return Translator.translatable(longDescription);
    }
    
    public void setLongDescription(String longDescription) {
        this.longDescription = longDescription;
    }
    
    public void completeTask(UUID uuid) {
        getData(uuid).completed = true;
        completeQuest(parent, uuid);
    }
    
    public abstract void onUpdate(Player player);
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public Quest getParent() {
        return parent;
    }
    
    public abstract float getCompletedRatio(Team team);
    
    public void mergeProgress(UUID playerId, QuestData own, QuestData other) {
        mergeProgress(playerId, getData(own), getData(other));
    }
    
    public abstract void mergeProgress(UUID playerId, Data own, Data other);
    
    public void resetData(UUID playerId) {
        parent.getQuestData(playerId).resetTaskData(id, this::newQuestData);
    }
    
    protected abstract void setComplete(Data data);
    
    public void copyProgress(QuestData own, QuestData other) {
        copyProgress(getData(own), getData(other));
    }
    
    public void copyProgress(Data own, Data other) {
        own.completed = other.completed;
    }
    
    public void onDelete() {
        EventTrigger.instance().remove(this);
    }
    
    public void register(EventTrigger.Type... types) {
        EventTrigger.instance().add(this, types);
    }
    
    public boolean isValid() {
        return getParent() != null && getParent().getTasks() != null && getParent().getTasks().contains(this);
    }
    
    //for these to be called one must register the task using the method above using the correct types
    public void onServerTick(MinecraftServer server) {
    }
    
    public void onPlayerTick(ServerPlayer playerEntity) {
    }
    
    public void onLivingDeath(LivingEntity entity, DamageSource source) {
    }
    
    public void onCrafting(Player player, ItemStack stack) {
    }
    
    public void onItemPickUp(Player playerEntity, ItemStack stack) {
    }
    
    public void onOpenBook(EventTrigger.BookOpeningEvent event) {
    }
    
    public void onReputationChange(EventTrigger.ReputationEvent event) {
    }
    
    public void onAnimalTame(Player tamer, Entity entity) {
    }
    
    public void onAdvancement(ServerPlayer playerEntity) {
    }
    
    public void onQuestCompleted(EventTrigger.QuestCompletedEvent event) {
    }
    
    public void onQuestSelected(EventTrigger.QuestSelectedEvent event) {
    }
    
    public void onBlockPlaced(Level world, BlockState state, LivingEntity entity) {
    }
    
    public void onBlockBroken(BlockPos blockPos, BlockState blockState, Player player) {
    }
    
    public void onItemUsed(Player playerEntity, Level world, InteractionHand hand) {
    }
    
    public void onBlockUsed(Player playerEntity, Level world, InteractionHand hand) {
    }
    
    public void onRemovedReputation(Reputation reputation) {
    }
    
    public void onRemovedRepMarker(ReputationMarker marker) {
    }
}
