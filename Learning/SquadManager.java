import java.util.ArrayList;
import java.util.List;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;

public class SquadManager {
	
	private static SquadManager instance = new SquadManager();
	private List<Squad> squadList = new ArrayList<Squad>();
	
	public static SquadManager getInstance() {
		return instance;
	}
	
	public boolean addSquad(Squad squad) {
		squadList.add(squad);
		return true;
	}
	
	
	public boolean addUnit(Unit unit) {
		for (Squad squad : squadList) {
			if(squad.isAddUnit(unit)) {
				squad.addUnit(unit);
				return true;
			}
		}
		
		return false;
	}
	
	public void update() {
		for (Squad squad : squadList) {
			squad.update();
		}
	}
	
	public void excuteCombat(String squadNm, Position targetPosition, Position fleePosition) {
		for (Squad squad : squadList) {
			if (squadNm.equals(squad.getName())) {
				squad.setTargetPosition(targetPosition);
				squad.setFleePosition(fleePosition);
				squad.setCombatMode(true);
			}
		}
	}
}