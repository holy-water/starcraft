import bwta.BaseLocation;

public class DangerousLocation {

	private BaseLocation baseLocation;			// 위험지역
	private int enemyCnt;						// 위험지역의 적 유닛 수 - 전투유닛 한정
	private AttackType attackType;				// 적의 공격 유형
	
	public enum AttackType {
		Air,		// 공중공격
		Ground,		// 지상공격
		Both		// 둘 다
	}

	public BaseLocation getBaseLocation() {
		return baseLocation;
	}

	public void setBaseLocation(BaseLocation baseLocation) {
		this.baseLocation = baseLocation;
	}

	public int getEnemyCnt() {
		return enemyCnt;
	}

	public void setEnemyCnt(int enemyCnt) {
		this.enemyCnt = enemyCnt;
	}

	public AttackType getAttackType() {
		return attackType;
	}

	public void setAttackType(AttackType attackType) {
		this.attackType = attackType;
	}
}
