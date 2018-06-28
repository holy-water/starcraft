## 1 (20180625) 환경설정

1. git clone https://github.com/holy-water/starcraft.git
2. 이클립스에서 git 프로젝트 내의 모든 폴더 Import 하는 방법
    - Package Explorer > New > Java Project > starcraft 이름으로 생성
    - 자동 연결
 
## 2 (20180626) TutorialBot 학습

1. pull 하는 과정에서 BasicBot/bin 쪽 폴더에서 CheckoutConflict 에러 발생
    - 전체 업데이트 지양
    - BasicBot/src & Learning 개별 업데이트

2. GameCommander : 게임 이벤트 처리, Controller
3. InformationManager : 게임 상황 정보 저장 및 업데이트
4. WorkerManager : 일꾼 관리 - Best Mineral 찾는 로직 다시 확인하기

## 3 (20180628) TutorialBot 학습 / 다크템플러
1. 건물 건설 조건
    - 해당 위치에 아무것도 없어야
    - 일꾼 유닛 죽으면 건설 중지

2. BuildManager : 건물 및 유닛 생산

3. 하나의 유닛을 만들기 위한 절차 - 빌드큐에 순차적으로 넣기
    - ex) supply Depot - Barracks - 마린

4. 궁금한 점
    - 서플라이가 부족하다는게 무슨 말?
    - supplyUse(인구수) + 6 > supplyTotal 이면 생산하게 해놓았는데 이게 뭘까
    - 나만 튜토리얼봇5 안보이는거야? wiki로만 코드 확인했다 ㅠ.ㅠ
    - 다크템플러봇
      + 질럿 한개 뽑았을 때 입구 정중앙에 H상태 유지해야 빌드 들키지 않는다고 한다
      + 이걸 우리쪽에선 그럼 알 수 없는 건가?
