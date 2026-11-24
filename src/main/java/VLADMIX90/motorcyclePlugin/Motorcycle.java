package VLADMIX90.motorcyclePlugin;

import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pig;

import java.util.UUID;

public final class Motorcycle {
    private final UUID id;
    private final Pig seat;
    private final ItemDisplay visual;
    private ItemDisplay lightVisual;
    private Pig passengerSeat;

    private double speed;
    private int battery;
    private boolean storageInProgress;
    private float headingYaw;
    private float persistedHeading = Float.NaN;
    private float visualRoll;
    private float visualPitch;
    private final double hoverPhase;
    private double jumpVelocity;
    private boolean jumping;
    private boolean jumpHeld;
    private int blockedTicks;
    private double batteryFraction;
    private double chargeFraction;
    private String gravibikeSkinId;
    private String gravibikeCustomModelData;
    private String gravibikeItemModel;

    public Motorcycle(UUID id, Pig seat, ItemDisplay visual, int battery, float headingYaw) {
        this.id = id;
        this.seat = seat;
        this.visual = visual;
        this.battery = battery;
        this.headingYaw = headingYaw;

        long mixed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        this.hoverPhase = (mixed & 0xFFFFL) / 65535.0 * Math.PI * 2.0;
    }

    public UUID id() { return id; }
    public Pig seat() { return seat; }
    public ItemDisplay visual() { return visual; }
    public ItemDisplay lightVisual() { return lightVisual; }
    public void setLightVisual(ItemDisplay lightVisual) { this.lightVisual = lightVisual; }
    public Pig passengerSeat() { return passengerSeat; }
    public void setPassengerSeat(Pig passengerSeat) { this.passengerSeat = passengerSeat; }
    public double speed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
    public int battery() { return battery; }
    public void setBattery(int battery) { this.battery = Math.max(0, battery); }
    public double batteryFraction() { return batteryFraction; }
    public void setBatteryFraction(double value) { batteryFraction = value; }
    public double chargeFraction() { return chargeFraction; }
    public void setChargeFraction(double value) { chargeFraction = value; }
    public boolean storageInProgress() { return storageInProgress; }
    public void setStorageInProgress(boolean value) { this.storageInProgress = value; }
    public float headingYaw() { return headingYaw; }
    public void setHeadingYaw(float headingYaw) { this.headingYaw = headingYaw; }
    public float persistedHeading() { return persistedHeading; }
    public void setPersistedHeading(float value) { persistedHeading = value; }
    public float visualRoll() { return visualRoll; }
    public void setVisualRoll(float value) { this.visualRoll = value; }
    public float visualPitch() { return visualPitch; }
    public void setVisualPitch(float value) { this.visualPitch = value; }
    public double hoverPhase() { return hoverPhase; }
    public double jumpVelocity() { return jumpVelocity; }
    public void setJumpVelocity(double value) { this.jumpVelocity = value; }
    public boolean jumping() { return jumping; }
    public void setJumping(boolean value) { this.jumping = value; }
    public boolean jumpHeld() { return jumpHeld; }
    public void setJumpHeld(boolean value) { this.jumpHeld = value; }
    /** Consecutive ticks the ground movement could not be carried out. */
    public int blockedTicks() { return blockedTicks; }
    public void setBlockedTicks(int value) { this.blockedTicks = Math.max(0, value); }
    public String gravibikeSkinId() { return gravibikeSkinId; }
    public String gravibikeItemModel() { return gravibikeItemModel; }
    public String gravibikeCustomModelData() { return gravibikeCustomModelData; }
    public void setGravibikeDecal(String skinId, String itemModel, String customModelData) {
        gravibikeSkinId = skinId;
        gravibikeItemModel = itemModel;
        gravibikeCustomModelData = customModelData;
    }
}
