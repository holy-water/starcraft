import bwta.BaseLocation;

public class DangerousLocation {

	private BaseLocation baseLocation;			// 위험지역
	private int enemyCnt;						// 위험지역의 적 유닛 수 - 전투유닛 한정
	private int groundCnt;						// 적의 지상 전투유닛 수
	private int airCnt;							// 적의 공중 전투유닛 수
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
	
	public int getGroundCnt() {
		return groundCnt;
	}

	public void setGroundCnt(int groundCnt) {
		this.groundCnt = groundCnt;
	}

	public int getAirCnt() {
		return airCnt;
	}

	public void setAirCnt(int airCnt) {
		this.airCnt = airCnt;
	}

	public AttackType getAttackType() {
		return attackType;
	}

	public void setAttackType(AttackType attackType) {
		this.attackType = attackType;
	}
}
