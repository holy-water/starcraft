public class OffenseManager {
	private static OffenseManager instance = new OffenseManager();

	/// static singleton 객체를 리턴합니다
	public static OffenseManager Instance() {
		return instance;
	}

	// 1. 벌쳐 마인 심기
	public void executeVultureMineManager() {
		VultureMineManager.Instance().update();
	}

	// 2. 견제
	public void executeCheckManager() {

	}
}
