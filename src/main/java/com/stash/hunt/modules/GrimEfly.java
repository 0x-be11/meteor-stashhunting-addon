package com.stash.hunt.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import com.stash.hunt.Addon;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static com.stash.hunt.Utils.*;

public class GrimEfly extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // TODO: Add setting to auto equip chestplate / hotbar elytra to prevent it breaking on reconnects

    private final Setting<Boolean> bounce = sgGeneral.add(new BoolSetting.Builder()
        .name("Bounce")
        .description("Automatically does bounce efly.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> lockPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("Lock Pitch")
        .description("Whether to lock your pitch when bounce is enabled.")
        .defaultValue(true)
        .visible(bounce::get)
        .build()
    );

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("Pitch")
        .description("The pitch to set when bounce is enabled.")
        .defaultValue(90.0)
        .visible(() -> bounce.get() && lockPitch.get())
        .build()
    );

    private final Setting<Boolean> lockYaw = sgGeneral.add(new BoolSetting.Builder()
        .name("Lock Yaw")
        .description("Whether to lock your yaw when bounce is enabled.")
        .defaultValue(false)
        .visible(bounce::get)
        .build()
    );

    private final Setting<Double> yaw = sgGeneral.add(new DoubleSetting.Builder()
        .name("Yaw")
        .description("The yaw to set when bounce is enabled.")
        .defaultValue(0.0)
        .visible(() -> bounce.get() && lockYaw.get())
        .build()
    );

    private final Setting<Boolean> highwayObstaclePasser = sgGeneral.add(new BoolSetting.Builder()
        .name("Highway Obstacle Passer")
        .description("Uses baritone to pass obstacles. Make sure to set the YAW lock as it uses this value to calculate the block to go to.")
        .defaultValue(false)
        .visible(bounce::get)
        .build()
    );

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("Distance")
        .description("The distance to set the baritone goal for path realignment.")
        .defaultValue(10.0)
        .visible(() -> bounce.get() && highwayObstaclePasser.get())
        .build()
    );

    private final Setting<Integer> targetY = sgGeneral.add(new IntSetting.Builder()
        .name("Y Level")
        .description("The Y level to bounce at.")
        .defaultValue(120)
        .visible(() -> bounce.get() && highwayObstaclePasser.get())
        .build()
    );

    private final Setting<Boolean> assumeHighwayDirs = sgGeneral.add(new BoolSetting.Builder()
        .name("Lock To Highway Directions")
        .description("Aligns you on the highways. Only works for the main 8 highway directions.")
        .defaultValue(true)
        .visible(() -> bounce.get() && highwayObstaclePasser.get())
        .build()
    );

    private final Setting<Boolean> avoidPortalTraps = sgGeneral.add(new BoolSetting.Builder()
        .name("Avoid Portal Traps")
        .description("Will attempt to detect portal traps on chunk load and avoid them.")
        .defaultValue(false)
        .visible(() -> bounce.get() && highwayObstaclePasser.get())
        .build()
    );

    private final Setting<Double> portalAvoidDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("Portal Avoid Distance")
        .description("The distance to a portal trap where the obstacle passer will takeover and go around it.")
        .defaultValue(20)
        .min(0)
        .sliderMax(50)
        .visible(() -> bounce.get() && highwayObstaclePasser.get() && avoidPortalTraps.get())
        .build()
    );

    private final Setting<Integer> portalScanWidth = sgGeneral.add(new IntSetting.Builder()
        .name("Portal Scan Width")
        .description("The width on the axis of the highway that will be scanned for portal traps.")
        .defaultValue(5)
        .min(3)
        .sliderMax(10)
        .visible(() -> bounce.get() && highwayObstaclePasser.get() && avoidPortalTraps.get())
        .build()
    );

    private final Setting<BlockPos> baritoneOffset = sgGeneral.add(new BlockPosSetting.Builder()
        .name("Baritone Offset")
        .description("The offset in blocks from where goals should be set.")
        .defaultValue(new BlockPos(0,0,0))
        .visible(() -> bounce.get() && highwayObstaclePasser.get())
        .build()
    );

    private final Setting<Boolean> autoEquipChestplate = sgGeneral.add(new BoolSetting.Builder()
        .name("Auto Equip Chestplate")
        .description("Equips a chestplate on activation. Fixes a bug on reconnect where you join wearing an elytra.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> paused = sgGeneral.add(new BoolSetting.Builder()
        .name("paused")
        .description("paused")
        .defaultValue(false)
        .visible(() -> false)
        .build()
    );

    public GrimEfly() {
        super(
            Addon.CATEGORY,
            "Grim-Efly",
            "Vanilla efly using a chestplate so that elytra does not use durability. (Requires elytra in hotbar)"
        );
    }

    private boolean startSprinting;
    private BlockPos portalTrap = null;

    @Override
    public void onActivate()
    {
        if (mc.player == null) return;
        startSprinting = mc.player.isSprinting();
        paused.set(false);
        tempPath = null;
        portalTrap = null;
        if (bounce.get())
        {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
        }

        if (autoEquipChestplate.get())
        {
            if (mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                info("Swapping");
                Modules.get().get(ChestSwap.class).swap();
            }
        }
    }

    @Override
    public void onDeactivate()
    {
        if (mc.player == null) return;

        if (bounce.get())
        {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
        }

        mc.player.setSprinting(startSprinting);
    }

    // 5 chunks forwards
    private final double maxDistance = 16 * 5;

    // a path used when there are no valid blocks in range.
    // it will instead path to this and then when it gets close it will look for a valid block again
    private BlockPos tempPath = null;

    @EventHandler
    private void onTick(TickEvent.Pre event)
    {
        if (mc.player == null || mc.player.getAbilities().allowFlying) return;

        mc.player.setSprinting(true);
        if (bounce.get())
        {
            if (tempPath != null && mc.player.getBlockPos().getSquaredDistance(tempPath) < 500)
            {
                tempPath = null;
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
            }
            else if (tempPath != null)
            {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(tempPath));
                return;
            }


            // if still pathing, wait for that to complete
            if (highwayObstaclePasser.get() && BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal() != null)
            {
                return;
            }
            // Length check to fix weird issue where goal gets set to 0 0 when going through queue, even though it gets reset. Likely due to bad connection.
            if (highwayObstaclePasser.get() && mc.player.getPos().length() > 100 && (mc.player.getY() < targetY.get()
                || mc.player.getY() > targetY.get() + 2
                || mc.player.horizontalCollision)
                || portalTrap != null && portalTrap.getSquaredDistance(mc.player.getBlockPos()) < portalAvoidDistance.get() * portalAvoidDistance.get())
            {
                paused.set(true);
                double targetYaw = lockYaw.get() ? yaw.get() : mc.player.getYaw();
                Vec3d pos;
                BlockPos goal = mc.player.getBlockPos();
                double currDistance = distance.get(); // Keep checking farther distances until a goal is found that has a block beneath it

                BlockPos startPos = mc.player.getBlockPos(); // The start pos is what we start looking for a valid position from
                if (portalTrap != null) {
                    startPos = portalTrap;
                    portalTrap = null;
                    info("Pathing around portal.");
                }

                do
                {
                    if (currDistance > maxDistance)
                    {
                        tempPath = goal;
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
                        return;
                    }
                    if (assumeHighwayDirs.get())
                    {
                        Vec3d playerPos = normalizedPositionOnAxis(startPos.toCenterPos()).multiply(startPos.toCenterPos().multiply(1,0,1).length());
                        pos = positionInDirection(playerPos, targetYaw, currDistance);
                    }
                    else
                    {
                        // TODO: Make this better
                        // Bug where currDistance always maxes out when on 1x2s next to highway
                        pos = positionInDirection(startPos.toCenterPos(), targetYaw, currDistance);
                    }
                    goal = new BlockPos((int)pos.x + baritoneOffset.get().getX(), targetY.get() + baritoneOffset.get().getY(), (int)pos.z + baritoneOffset.get().getZ());
                    currDistance++;
                }
                // avoid pathing on air cause baritone freaks out, and dont path into portals in case a mod is avoiding portals
                while (mc.world.getBlockState(goal.down()).isAir() || mc.world.getBlockState(goal).getBlock() == Blocks.NETHER_PORTAL);
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(goal));
            }
            else
            {
                // keep jumping
                paused.set(false);
                if (mc.player.isOnGround())
                {
                    mc.player.jump();
                }

                // set yaw and pitch
                if (lockYaw.get())
                {
                    mc.player.setYaw(yaw.get().floatValue());
                }
                if (lockPitch.get())
                {
                    mc.player.setPitch(pitch.get().floatValue());
                }
            }
        }

        if (!paused.get())
        {
            doGrimEflyStuff();

        }
    }



    @EventHandler
    private void onChunkData(ChunkDataEvent event)
    {
        if (!avoidPortalTraps.get()) return;
        ChunkPos pos = event.chunk().getPos();

        BlockPos centerPos = pos.getCenterAtY(targetY.get());

        // Check if chunk is on the players path
        Vec3d moveDir = yawToDirection(yaw.get());
        double distanceToHighway = distancePointToDirection(Vec3d.of(centerPos), moveDir);

        if (distanceToHighway > 21) return;

        for (int x = 0; x < 16; x++)
        {
            for (int z = 0; z < 16; z++)
            {
                for (int y = targetY.get(); y < targetY.get() + 3; y++)
                {
                    BlockPos position = new BlockPos(pos.x * 16 + x, y, pos.z * 16 + z);

                    if (distancePointToDirection(Vec3d.of(position), moveDir) > portalScanWidth.get()) continue;

                    if (mc.world.getBlockState(position).getBlock().equals(Blocks.NETHER_PORTAL)) // TODO: This position could be unloaded
                    {
                        BlockPos posBehind = new BlockPos((int)Math.floor(position.getX() + moveDir.x), position.getY(), (int) Math.floor(position.getZ() + moveDir.z));

                        // Trap is detected when a portal has a solid block or another portal behind it
                        if (mc.world.getBlockState(posBehind).isSolidBlock(mc.world, posBehind) ||
                            mc.world.getBlockState(posBehind).getBlock() == Blocks.NETHER_PORTAL)
                        {
                            if (portalTrap == null || (
                                portalTrap.getSquaredDistance(posBehind) > 100 &&
                                mc.player.getBlockPos().getSquaredDistance(posBehind) < mc.player.getBlockPos().getSquaredDistance(portalTrap))
                            )
                            {
                                portalTrap = posBehind;
                            }
                        }
                    }
                }
            }
        }
    }

    private void doGrimEflyStuff()
    {
        FindItemResult itemResult = InvUtils.findInHotbar(Items.ELYTRA);
        if (!itemResult.found()) return;

        swapToItem(itemResult.slot());

        sendStartFlyingPacket();

        swapToItem(itemResult.slot());

        if (Utils.canOpenGui()) {
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        }
    }

    Vec3d normalizedPositionOnAxis(Vec3d pos) {
        double angle = -Math.atan2(pos.x, pos.z);
        double angleDeg = Math.toDegrees(angle);

        double ordinalAngle = Math.round(angleDeg / 45.0f) * 45;

        return positionInDirection(new Vec3d(0,0,0), ordinalAngle, 1);
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event)
    {
        List<Identifier> armorEquipSounds = List.of(
            Identifier.of("minecraft:item.armor.equip_generic"),
            Identifier.of("minecraft:item.armor.equip_netherite"),
            Identifier.of("minecraft:item.armor.equip_diamond"),
            Identifier.of("minecraft:item.armor.equip_gold"),
            Identifier.of("minecraft:item.armor.equip_iron"),
            Identifier.of("minecraft:item.armor.equip_chain"),
            Identifier.of("minecraft:item.armor.equip_leather"),
            Identifier.of("minecraft:item.elytra.flying")
        );
        for (Identifier identifier : armorEquipSounds) {
            if (identifier.equals(event.sound.getId())) {
                event.cancel();
                break;
            }
        }
    }

    // 38 is the meteor mapping for chestplate
    // serverside uses default mappings: https://imgs.search.brave.com/cyvAxjIhLweeF1qeRXpC_8ESRlImhUmMGWbV_n2to_A/rs:fit:860:0:0:0/g:ce/aHR0cHM6Ly9jNGsz/LmdpdGh1Yi5pby93/aWtpLnZnL2ltYWdl/cy8xLzEzL0ludmVu/dG9yeS1zbG90cy5w/bmc
    private void swapToItem(int slot) {
        ItemStack chestItem = mc.player.getInventory().getStack(38);
        ItemStack hotbarSwapItem = mc.player.getInventory().getStack(slot);

        Int2ObjectMap<ItemStack> changedSlots = new Int2ObjectOpenHashMap<>();
        changedSlots.put(6, hotbarSwapItem);
        changedSlots.put(slot + 36, chestItem);

        sendSwapPacket(changedSlots, slot);
    }

    private void sendStartFlyingPacket() {
        if (mc.player == null) return;
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(
            mc.player,
            ClientCommandC2SPacket.Mode.START_FALL_FLYING
        ));
    }

    private void sendSwapPacket(Int2ObjectMap<ItemStack> changedSlots, int buttonNum) {
        int syncId  = mc.player.currentScreenHandler.syncId;
        int stateId = mc.player.currentScreenHandler.getRevision();

        // "slotNum = 6"
        // "buttonNum = 0"
        // "SlotActionType.SWAP"
        // "changedSlots" as built above
        mc.player.networkHandler.sendPacket(new ClickSlotC2SPacket(
            syncId,
            stateId,
            6,                 // slotNum
            buttonNum,                 // buttonNum: the slot number thats being swapped
            SlotActionType.SWAP,
            new ItemStack(Items.AIR), // clickedItem
            changedSlots
        ));
    }
}
