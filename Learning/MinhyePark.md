## (20180705) scv 리밸런싱 문제 해결 계속

- 어떻게 하면 scv가 공격받는 지역으로 되돌아 가지 않을까? 
1. 해당 지역의 커맨드 센터가 공격받는지 여부 확인? 
    - 순간적으로 공격받지 않을 수도 있고, 지역 전체에 대한 정보는 될 수 없음

2. 해당 지역 내에 상대편 유닛이 얼마나 많은지 확인?
    - getUnitsInRectangle / getUnitsInRadius 이용하여 해당 지역 내의 상대 유닛이 몇 마리나 있는지 확인 가능
    - 많고 적음의 기준이 필요함

## (20180704) WorkerManager 수정

1. 해결해야할 문제
    - scv가 도망치는 것과 리밸런싱 되는 것 사이의 충돌 해결 필요

2. 상황 정리
    1. 전제조건
        - Idle 상태가 되면 미네랄일꾼으로 변경되면서 리밸런싱 일어남
        - 리밸런싱은 덜 찬 상태의 커맨드센터가 우선순위(거리 순), but 모두 꽉 찼으면 남는 미네랄 있는 진영으로 이동
    2. 커맨드센터 파괴될 경우 > 해당 진영의 일꾼 Idle 상태로 전환
    3. 일꾼이 죽을 경우 > 특정 진영에 미네랄 수보다 많은 일꾼이 있으면 그 일꾼 수만큼 Idle 상태로 전환
    4. 공격당해서 도망온 일꾼은 Idle 상태로 전환
        - 공격받았던 지역의 커맨드 센터 여전히 있다면, 도망온 곳이 꽉 찼을 떄 다시 돌아갈 수 있음 > 문제 상황으로 추정
        - 공격받았던 지역의 커맨드 센터가 파괴됐다면, 도망온 곳이 꽉 찼어도 돌아가지 않음
    
3. 여러 가지 생각 (두목님의 검토 필요)
    1. scv가 공격 당했을 때 도망가는 것을 scv 한 개체마다 판단하는게 맞는 건가요? 헷갈려요.
        - 단순히 하나가 공격 당했다고 해서 전체가 움직이는 것도 이상한데,
        - 주변 건물이 파괴되고 진영 전반이 공격을 당하는 상황인데, scv가 자기는 안맞았다고 가만히 있는 것도 이상해요.
    2. 커맨드 센터가 파괴되었을 경우에는 mineralAndMineralWorkerRatio = 2로 바꿔보는 건 어떨지?
        - 일단 보류
4. 결과
    - 원하는 상황이 나오지 않아서 파악이 안됨 ㅠㅠ

5. 궁금한 점
    - 게임 진행이 원래 이렇게 점점 느려지는 건가요?
    - 무작정 쳐들어 오는 애들에 대해선 어떻게 대처해야 하는가 ㅠㅠ 속수무책으로 당하고 그냥 끝남 ㅠㅠ
    - 게임 막 시작했을 때 이 에러 나만 뜨나요?
      + loadGameRecord failed. Could not open file :c:\starcraft\bwapi-data\read\NoNameBot_GameRecord.dat
    
## (20180703) WorkerManager 수정

1. 해결해야할 문제
    - scv가 공격당했을 경우, 해당 scv의 위치가 본진이면 앞마당으로, 앞마당이면 본진으로 이동하도록 설정
    - 이동 중 다른 명령과 충돌되지 않도록 설정할 필요
    
2. 해결 방법
    - scv의 역할을 Move로 설정하면 다른 명령의 영향을 받지 않게 됨
    - WorkerManager.java > updateWorkerStatus() 수정
      + 공격을 받았을 경우 (isUnderAttack())
      + 본진의 Region / 앞마당의 Region 을 각각 계산
      + worker의 Region과 비교
      + worker가 다른 진영으로 이동할 수 있도록 job(Move), WorkerMoveData 설정

3. 아직 테스트는 못한 상태 ㅠㅠ

## (20180701) 강의듣기

이영호 테란 초보 강의 <https://youtu.be/STvspTUYlsU>
1. 가스가 88이 되면 두마리를 뺀다. 더블을 가려면 미네랄이 많이 필요하기 때문
2. 가스가 100이 되면 팩토리를 짓는다.
3. 파일런이 세개 이상이면 다크일 가능성 하락, 혹은 다크여도 막을 수 있음 > 아카데미 건설 > 아카데미 완성되면 스캔 달기
    - 이 때 바로 스캔 뿌리지 말고 엔베 짓기 > 엔베 7-80프로 완성되었을 때 스캔 뿌리기
4. 시즈모드업? 마인업?

점점 무슨 말인지 모르겠다 ㅠㅠ

## (20180630) WorkerManager 분석

1. 현 상태 분석
    1. Idle 상태의 Worker가 있을 경우(커맨드 센터 파괴시 포함) > 가까운 커맨드 센터 선택하여 미네랄 채취하게 함
        - handleIdleWorkers() > setMineralWorker(worker) > getClosestResourceDepotFromWorker()
        - getClosestResourceDepotFromWorker() : 커맨드센터 선택 기준
            1. (전제조건 외) ① 미네랄 일꾼 수가 꽉 차지 않은 곳 ② 가까운 곳
            2. 없으면, 미네랄이 남은 커맨드센터 선택
            3. 없으면, 가장 가까운 커맨드센터 선택
    2. 커맨드 센터 신규 생성 시 / 일꾼 신규 생성 시 / 일꾼 죽었을 경우 > Worker들을 리밸런스
        - rebalanceWorkers(): 미네랄 일꾼 수가 꽉 찼을 경우 worker들을 idle 상태로 변경
    3. depotHasEnoughMineralWorkers()
        - 미네랄 일꾼 수가 꽉 찼는지 확인, true이면 꽉 찬 것, false이면 아직 여유있는 것
        - 미네랄 일꾼 수가 꽉 찼다 = 커맨드센터 근처 미네랄 수 * 2 로 현재 고정

2. 문제점: 멀티 기지로 갈 경우 scv 재분배가 이뤄지지 않는다
3. 해결방안 : 미네랄 일꾼 수가 꽉 찬 기준을 조정
      - 현재 일꾼 수 = 미네랄수*2 이면 꽉찬 것으로 판단
      - 2를 상수로 두지 않고 변수로 둬야할 것으로 추정
      - 초기값은 1로 시작하고 최대값은 2로 되도록 설정해야되는 건가?
      - 어떤 식으로 하지..?
      - WorkerData.java > 생성자 부분의 mineralAndMineralWorkerRatio = 1로 수정 

## (20180628) TutorialBot 학습 / 다크템플러

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

## (20180626) TutorialBot 학습

1. pull 하는 과정에서 BasicBot/bin 쪽 폴더에서 CheckoutConflict 에러 발생
    - 전체 업데이트 지양
    - BasicBot/src & Learning 개별 업데이트

2. GameCommander : 게임 이벤트 처리, Controller
3. InformationManager : 게임 상황 정보 저장 및 업데이트
4. WorkerManager : 일꾼 관리 - Best Mineral 찾는 로직 다시 확인하기

## (20180625) 환경설정

1. git clone https://github.com/holy-water/starcraft.git
2. 이클립스에서 git 프로젝트 내의 모든 폴더 Import 하는 방법
    - Package Explorer > New > Java Project > starcraft 이름으로 생성
    - 자동 연결
 