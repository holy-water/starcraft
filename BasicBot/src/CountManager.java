import bwapi.UnitType;

// 건물의 개수를 관리하는 클래스
public class CountManager {

	private static CountManager instance = new CountManager();

	private int factory;

	private int machineShop;

	private int engineeringBay;

	private int armory;

	private int refinery;

	private int completedFactory;

	private int completedMachineShop;

	public void update() {
		this.machineShop = Math.max(this.machineShop,
				MyBotModule.Broodwar.self().allUnitCount(UnitType.Terran_Machine_Shop));

		this.factory = Math.max(this.factory, MyBotModule.Broodwar.self().allUnitCount(UnitType.Terran_Factory));

		this.refinery = Math.max(this.refinery, MyBotModule.Broodwar.self().allUnitCount(UnitType.Terran_Refinery));

		this.completedMachineShop = MyBotModule.Broodwar.self().completedUnitCount(UnitType.Terran_Machine_Shop);

		this.completedFactory = MyBotModule.Broodwar.self().completedUnitCount(UnitType.Terran_Factory);
	}

	public static CountManager Instance() {
		return instance;
	}

	public int getFactory() {
		return factory;
	}

	public void setFactory() {
		this.factory++;
	}

	public int getMachineShop() {
		return machineShop;
	}

	public void setMachineShop() {
		this.machineShop++;
	}

	public int getEngineeringBay() {
		return engineeringBay;
	}

	public void setEngineeringBay() {
		this.engineeringBay++;
	}

	public int getArmory() {
		return armory;
	}

	public void setArmory() {
		this.armory++;
	}

	public int getRefinery() {
		return refinery;
	}

	public void setRefinery() {
		this.refinery++;
	}

	public int getCompletedFactory() {
		return completedFactory;
	}

	public void setCompletedFactory() {
		this.completedFactory++;
	}

	public int getCompletedMachineShop() {
		return completedMachineShop;
	}

	public void setCompletedMachineShop() {
		this.completedMachineShop++;
	}
}
