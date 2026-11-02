package com.salvagehelper;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(SalvageHelperConfig.GROUP)
public interface SalvageHelperConfig extends Config
{
	String GROUP = "salvagehelper";

	enum NextStepMode
	{
		OFF("Off"),
		AUTOMATIC("Automatic"),
		DEPOSIT("Deposit only");

		private final String name;

		NextStepMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum DepositTrigger
	{
		ANYTHING("Anything the hold accepts"),
		SALVAGE_ONLY("Salvage only"),
		WHITELIST("Only my list");

		private final String name;

		DepositTrigger(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum BoostWarning
	{
		OFF("Off"),
		BEFORE("Before it drops"),
		AFTER("After it drops"),
		BOTH("Both");

		private final String name;

		BoostWarning(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	enum CrewTextMode
	{
		OFF("Off"),
		OWN_BOAT("My boat"),
		ALL_BOATS("All boats");

		private final String name;

		CrewTextMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	// ---- Sections ----

	@ConfigSection(name = "Cargo hold", description = "Left-click deposit on the cargo hold", position = 0)
	String cargoSection = "cargoSection";

	@ConfigSection(name = "Cargo hold alerts", description = "Count, limit and full-hold alerts", position = 1)
	String cargoAlertSection = "cargoAlertSection";

	@ConfigSection(name = "Banner", description = "The big banner at the top of the game", position = 2)
	String bannerSection = "bannerSection";

	@ConfigSection(name = "Hook stopped alert", description = "Infobox and notification when salvaging stops", position = 3)
	String hookAlertSection = "hookAlertSection";

	@ConfigSection(name = "Reminders", description = "Hooks, crew, boosts, idle logout and danger", position = 4)
	String reminderSection = "reminderSection";

	@ConfigSection(name = "Crewmates", description = "Hiding idle crewmates and crew chatter", position = 5)
	String crewSection = "crewSection";

	@ConfigSection(name = "Wrecks", description = "Wreck outlines, timers, reach and double spot boxes", position = 6)
	String wreckSection = "wreckSection";

	@ConfigSection(name = "Next step", description = "Outline what to click next on your boat", position = 7)
	String nextStepSection = "nextStepSection";

	@ConfigSection(name = "Salvage counter", description = "Overlay with salvage and Sailing XP rates", position = 8)
	String counterSection = "counterSection";

	// ---- Cargo hold ----

	@ConfigItem(keyName = "leftClickDepositAll", name = "Left-click deposit all",
		description = "Makes the cargo hold's deposit option the left-click while your inventory holds something depositable",
		section = cargoSection, position = 0)
	default boolean leftClickDepositAll()
	{
		return true;
	}

	@ConfigItem(keyName = "depositOptions", name = "Deposit option names",
		description = "Comma-separated cargo hold options to try first, in order. Any other deposit option except Deposit-held is used if none match",
		section = cargoSection, position = 1)
	default String depositOptions()
	{
		return "Deposit-all, Quick-deposit, Deposit";
	}

	@ConfigItem(keyName = "depositTrigger", name = "Swap when carrying",
		description = "Which inventory items make deposit the left-click: anything the hold accepts (salvage, fish, cannonballs, repair kits, drinks...), salvage only, or only the items in your list below",
		section = cargoSection, position = 2)
	default DepositTrigger depositTrigger()
	{
		return DepositTrigger.ANYTHING;
	}

	@ConfigItem(keyName = "depositWhitelist", name = "My list",
		description = "Used with \"Only my list\": comma-separated item names or IDs, e.g. Large salvage, *salvage, Rune cannonball, 32853. * matches anything",
		section = cargoSection, position = 3)
	default String depositWhitelist()
	{
		return "*salvage";
	}

	@ConfigItem(keyName = "extraDepositableIds", name = "Extra depositable item IDs",
		description = "Comma-separated item IDs that also trigger the swap in the other two modes",
		section = cargoSection, position = 4)
	default String extraDepositableIds()
	{
		return "";
	}

	// ---- Cargo hold alerts ----

	@ConfigItem(keyName = "cargoInfobox", name = "Show cargo infobox",
		description = "Shows how many hold slots are used while you're on your boat. Right-click it to change the alert limit",
		section = cargoAlertSection, position = 0)
	default boolean cargoInfobox()
	{
		return true;
	}

	@Range(min = 1, max = 240)
	@ConfigItem(keyName = "cargoLimit", name = "Alert at",
		description = "Alert once the cargo hold uses this many slots. Can also be set by right-clicking the infobox",
		section = cargoAlertSection, position = 1)
	default int cargoLimit()
	{
		return 50;
	}

	@ConfigItem(keyName = "cargoLimitNotification", name = "Notify at limit",
		description = "Notification when the hold reaches your limit",
		section = cargoAlertSection, position = 2)
	default Notification cargoLimitNotification()
	{
		return Notification.ON;
	}

	@ConfigItem(keyName = "limitBanner", name = "Banner at limit",
		description = "Shows an orange banner while the hold is at or over your limit but not full",
		section = cargoAlertSection, position = 3)
	default boolean limitBanner()
	{
		return true;
	}

	@ConfigItem(keyName = "fullHoldNotification", name = "Notify when full",
		description = "Notification when the cargo hold is completely full",
		section = cargoAlertSection, position = 4)
	default Notification fullHoldNotification()
	{
		return Notification.ON;
	}

	@Range(max = 60)
	@Units(Units.MINUTES)
	@ConfigItem(keyName = "fullRepeatMinutes", name = "Repeat full alert every",
		description = "Send the full-hold notification again this often while it stays full. 0 sends it once",
		section = cargoAlertSection, position = 5)
	default int fullRepeatMinutes()
	{
		return 0;
	}

	@ConfigItem(keyName = "countOnHold", name = "Count on the hold",
		description = "Writes the slot count on the cargo hold itself, e.g. 39/160, fading from green to red as it fills",
		section = cargoAlertSection, position = 6)
	default boolean countOnHold()
	{
		return true;
	}

	@ConfigItem(keyName = "etaInfobox", name = "Time-to-full infobox",
		description = "Infobox with how long until the hold is full at the recent fill rate",
		section = cargoAlertSection, position = 7)
	default boolean etaInfobox()
	{
		return false;
	}

	@ConfigItem(keyName = "boxSalvageInHold", name = "Box salvage in the hold",
		description = "While the cargo hold is open, draws a box round the salvage in it",
		section = cargoAlertSection, position = 8)
	default boolean boxSalvageInHold()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "holdSalvageColor", name = "Salvage box colour",
		description = "Colour of the boxes round salvage in the hold",
		section = cargoAlertSection, position = 9)
	default Color holdSalvageColor()
	{
		return new Color(60, 220, 230);
	}

	// ---- Banner ----

	@ConfigItem(keyName = "showBanner", name = "Show banner",
		description = "Big banner at the top of the game while the hold is full or something needs you",
		section = bannerSection, position = 0)
	default boolean showBanner()
	{
		return true;
	}

	@ConfigItem(keyName = "flashBanner", name = "Flash banner",
		description = "Pulses the banner so it catches your eye",
		section = bannerSection, position = 1)
	default boolean flashBanner()
	{
		return true;
	}

	@Range(min = 12, max = 48)
	@ConfigItem(keyName = "bannerTextSize", name = "Text size",
		description = "Banner text size in points",
		section = bannerSection, position = 2)
	default int bannerTextSize()
	{
		return 22;
	}

	@Alpha
	@ConfigItem(keyName = "fullBannerColor", name = "Full / danger colour",
		description = "Banner background when the hold is full or you are in danger",
		section = bannerSection, position = 3)
	default Color fullBannerColor()
	{
		return new Color(190, 30, 30, 220);
	}

	@Alpha
	@ConfigItem(keyName = "limitBannerColor", name = "At limit colour",
		description = "Banner background when the hold reaches your limit",
		section = bannerSection, position = 4)
	default Color limitBannerColor()
	{
		return new Color(220, 110, 20, 220);
	}

	@Alpha
	@ConfigItem(keyName = "reminderBannerColor", name = "Reminder colour",
		description = "Banner background for reminders",
		section = bannerSection, position = 5)
	default Color reminderBannerColor()
	{
		return new Color(180, 140, 20, 220);
	}

	// ---- Hook stopped alert ----

	@ConfigItem(keyName = "hookStoppedInfobox", name = "Show stopped infobox",
		description = "Shows an infobox with your message while no salvaging is happening",
		section = hookAlertSection, position = 0)
	default boolean hookStoppedInfobox()
	{
		return true;
	}

	@Range(min = 3, max = 600)
	@Units(Units.SECONDS)
	@ConfigItem(keyName = "hookStoppedSeconds", name = "Stopped after",
		description = "How long without salvaging activity before the alert fires",
		section = hookAlertSection, position = 1)
	default int hookStoppedSeconds()
	{
		return 12;
	}

	@ConfigItem(keyName = "hookStoppedMessage", name = "Message",
		description = "Text shown on the infobox and in the notification. Only about 6 characters fit on the infobox itself; hover it for the full text",
		section = hookAlertSection, position = 2)
	default String hookStoppedMessage()
	{
		return "Hooks stopped!";
	}

	@ConfigItem(keyName = "hookStoppedNotification", name = "Notify when stopped",
		description = "Sends a notification with your message when salvaging stops",
		section = hookAlertSection, position = 3)
	default Notification hookStoppedNotification()
	{
		return Notification.ON;
	}

	// ---- Reminders ----

	@Range(max = 120)
	@Units(Units.SECONDS)
	@ConfigItem(keyName = "reminderGraceSeconds", name = "Hook reminder delay",
		description = "How long a hook may stand empty or idle before the first reminder",
		section = reminderSection, position = 0)
	default int reminderGraceSeconds()
	{
		return 10;
	}

	@Range(max = 600)
	@Units(Units.SECONDS)
	@ConfigItem(keyName = "reminderRepeatSeconds", name = "Repeat empty-hook every",
		description = "Remind again this often while a hook stays empty. 0 reminds once",
		section = reminderSection, position = 1)
	default int reminderRepeatSeconds()
	{
		return 60;
	}

	@ConfigItem(keyName = "hookEmptyNotification", name = "Hook empty, crew free",
		description = "A hook is empty while a crewmate sits idle and a wreck is up in reach",
		section = reminderSection, position = 2)
	default Notification hookEmptyNotification()
	{
		return Notification.ON;
	}

	@ConfigItem(keyName = "hookIdleNotification", name = "Your hook is idle",
		description = "You are at a hook with nothing to salvage while a wreck is up in reach (you don't restart by yourself)",
		section = reminderSection, position = 3)
	default Notification hookIdleNotification()
	{
		return Notification.ON;
	}

	@Range(min = 15, max = 600)
	@Units(Units.SECONDS)
	@ConfigItem(keyName = "crewStoppedSeconds", name = "Crew stopped after",
		description = "How long crew on hooks may go without hooking anything, with a wreck in reach, before the reminder",
		section = reminderSection, position = 4)
	default int crewStoppedSeconds()
	{
		return 60;
	}

	@ConfigItem(keyName = "crewStoppedNotification", name = "Crew stopped at a wreck",
		description = "Your crew stopped bringing salvage in although a wreck is in reach. Reassigning them or nudging the boat usually fixes it",
		section = reminderSection, position = 5)
	default Notification crewStoppedNotification()
	{
		return Notification.ON;
	}

	@ConfigItem(keyName = "boostWarning", name = "Boost warning",
		description = "When a wreck in reach needs a boosted Sailing level: warn just before the boost drops below it, after it has, or both",
		section = reminderSection, position = 6)
	default BoostWarning boostWarning()
	{
		return BoostWarning.BOTH;
	}

	@ConfigItem(keyName = "boostNotification", name = "Boost notification",
		description = "Notification for the boost warning",
		section = reminderSection, position = 7)
	default Notification boostNotification()
	{
		return Notification.ON;
	}

	@Range(max = 240)
	@Units(Units.SECONDS)
	@ConfigItem(keyName = "idleLogoutWarnSeconds", name = "Warn before idle logout",
		description = "While on your boat, warn this long before the game logs you out for idling. 0 never warns",
		section = reminderSection, position = 8)
	default int idleLogoutWarnSeconds()
	{
		return 60;
	}

	@ConfigItem(keyName = "idleLogoutNotification", name = "Idle logout notification",
		description = "Notification for the idle logout warning",
		section = reminderSection, position = 9)
	default Notification idleLogoutNotification()
	{
		return Notification.ON;
	}

	@ConfigItem(keyName = "dangerNotification", name = "Danger",
		description = "While at wrecks on your boat: the boat takes damage, you are hit, or you are poisoned. At most every 30 seconds",
		section = reminderSection, position = 10)
	default Notification dangerNotification()
	{
		return Notification.ON;
	}

	@ConfigItem(keyName = "dangerBanner", name = "Danger banner",
		description = "Red DANGER banner for ten seconds after the last hit, or while poisoned",
		section = reminderSection, position = 11)
	default boolean dangerBanner()
	{
		return true;
	}

	// ---- Crewmates ----

	@ConfigItem(keyName = "hideIdleCrewmates", name = "Hide idle crewmates",
		description = "While on your own boat, hides your crewmates who are not operating a salvaging hook",
		section = crewSection, position = 0)
	default boolean hideIdleCrewmates()
	{
		return false;
	}

	@Range(max = 20)
	@Units(Units.TICKS)
	@ConfigItem(keyName = "graceTicks", name = "Grace period",
		description = "How many ticks a crewmate may stop the hook animation before being hidden",
		section = crewSection, position = 1)
	default int graceTicks()
	{
		return 3;
	}

	@ConfigItem(keyName = "hookProximityFallback", name = "Count crew next to a hook",
		description = "Only for crewmates the game's job list can't be matched to: also treat one standing next to a hook as on it",
		section = crewSection, position = 2)
	default boolean hookProximityFallback()
	{
		return false;
	}

	@ConfigItem(keyName = "hideCrewText", name = "Hide crew text",
		description = "Hides the text over crewmates' heads and players' orders to their crew, on your boat or on every boat",
		section = crewSection, position = 3)
	default CrewTextMode hideCrewText()
	{
		return CrewTextMode.OFF;
	}

	// ---- Wrecks ----

	@ConfigItem(keyName = "outlineWrecks", name = "Outline wrecks",
		description = "Outlines each wreck that is up",
		section = wreckSection, position = 0)
	default boolean outlineWrecks()
	{
		return true;
	}

	@ConfigItem(keyName = "wreckTimers", name = "Timers on wrecks",
		description = "How long each wreck has left at most, and when the next can rise. Upper limits: a wreck's clock starts when anyone first salvages it",
		section = wreckSection, position = 1)
	default boolean wreckTimers()
	{
		return true;
	}

	@ConfigItem(keyName = "reachBoxes", name = "Reach boxes",
		description = "Boxes the sea where a hook can reach each wreck",
		section = wreckSection, position = 2)
	default boolean reachBoxes()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "reachColor", name = "Wreck colour",
		description = "Colour of wreck outlines and reach boxes",
		section = wreckSection, position = 3)
	default Color reachColor()
	{
		return new Color(255, 215, 40, 200);
	}

	@ConfigItem(keyName = "doubleSpotBoxes", name = "Double spot boxes",
		description = "Boxes the sea where one hook reaches two wrecks at once",
		section = wreckSection, position = 4)
	default boolean doubleSpotBoxes()
	{
		return true;
	}

	@Alpha
	@ConfigItem(keyName = "doubleSpotColor", name = "Double spot colour",
		description = "Colour of double spot boxes while you are not parked in one",
		section = wreckSection, position = 5)
	default Color doubleSpotColor()
	{
		return new Color(70, 220, 90, 220);
	}

	@Alpha
	@ConfigItem(keyName = "parkedColor", name = "Parked colour",
		description = "Colour a double spot box changes to once every hook on your boat is inside it",
		section = wreckSection, position = 6)
	default Color parkedColor()
	{
		return new Color(51, 255, 255, 220);
	}

	@ConfigItem(keyName = "labelDoubleSpots", name = "Label double spots",
		description = "Writes \"Park here (1/2)\" on each double spot box, and \"Parked\" once every hook is in",
		section = wreckSection, position = 7)
	default boolean labelDoubleSpots()
	{
		return true;
	}

	@ConfigItem(keyName = "hideReachWhenParked", name = "Hide reach boxes when parked",
		description = "Takes the reach boxes away while every hook is inside a double spot",
		section = wreckSection, position = 8)
	default boolean hideReachWhenParked()
	{
		return true;
	}

	@ConfigItem(keyName = "fillBoxes", name = "Fill the boxes",
		description = "Shades the inside of reach and double spot boxes",
		section = wreckSection, position = 9)
	default boolean fillBoxes()
	{
		return true;
	}

	// ---- Next step ----

	@ConfigItem(keyName = "nextStep", name = "Highlight next step",
		description = "Automatic: the salvaging station while you carry salvage (the hold to take more out), or on boats without a station the hold once your inventory is full. Deposit only: always the hold once full. Both then a free hook",
		section = nextStepSection, position = 0)
	default NextStepMode nextStep()
	{
		return NextStepMode.AUTOMATIC;
	}

	@Alpha
	@ConfigItem(keyName = "nextStepColor", name = "Highlight colour",
		description = "Outline colour for the next step",
		section = nextStepSection, position = 1)
	default Color nextStepColor()
	{
		return new Color(255, 170, 0, 220);
	}

	// ---- Salvage counter ----

	@ConfigItem(keyName = "showSalvageCounter", name = "Show salvage counter",
		description = "Shows or hides the whole salvage counter box",
		section = counterSection, position = 0)
	default boolean showSalvageCounter()
	{
		return true;
	}

	@ConfigItem(keyName = "counterTitle", name = "Title",
		description = "Shows the \"Salvage\" title at the top of the box",
		section = counterSection, position = 1)
	default boolean counterTitle()
	{
		return true;
	}

	@ConfigItem(keyName = "counterInventory", name = "Inventory",
		description = "Salvage in your inventory",
		section = counterSection, position = 2)
	default boolean counterInventory()
	{
		return true;
	}

	@ConfigItem(keyName = "counterCargoHold", name = "Cargo hold",
		description = "Salvage in your cargo hold (~ while estimated, ? until you open the hold)",
		section = counterSection, position = 3)
	default boolean counterCargoHold()
	{
		return true;
	}

	@ConfigItem(keyName = "holdFullLine", name = "Hold full in",
		description = "How long until the cargo hold is full at the recent fill rate",
		section = counterSection, position = 4)
	default boolean holdFullLine()
	{
		return true;
	}

	@ConfigItem(keyName = "counterGained", name = "Gained",
		description = "Salvage gained this session, by you and your crew",
		section = counterSection, position = 5)
	default boolean counterGained()
	{
		return true;
	}

	@ConfigItem(keyName = "counterSalvagePerHour", name = "Salvage/hr",
		description = "Salvage gained per hour this session",
		section = counterSection, position = 6)
	default boolean counterSalvagePerHour()
	{
		return true;
	}

	@ConfigItem(keyName = "counterXpGained", name = "XP gained",
		description = "Sailing XP gained this session",
		section = counterSection, position = 7)
	default boolean counterXpGained()
	{
		return true;
	}

	@ConfigItem(keyName = "counterXpPerHour", name = "XP/hr",
		description = "Sailing XP per hour this session",
		section = counterSection, position = 8)
	default boolean counterXpPerHour()
	{
		return true;
	}

	@Range(min = 1, max = 120)
	@Units(Units.MINUTES)
	@ConfigItem(keyName = "counterResetMinutes", name = "Reset after idle",
		description = "Starts a new session after this long without gaining salvage",
		section = counterSection, position = 9)
	default int counterResetMinutes()
	{
		return 10;
	}
}
