## (20180803) 수정사항

1. 모든 것이 이상하다.. ^_^
2. 정찰도 자꾸 본진을 정찰하려고 하고
3. 적이 본진에 있을때 상황 판단하는게 생각보다 내 맘대로 안되고 ㅠㅠ
4. 건물이 있을때 포지션을 찍어주는데 다같이 몰려가서 어택은 한놈만 하고
5. 암튼 다 이상하다 ㅎㅎㅎㅎㅎ 어째야 할지 모르겠다. 할게 많은데

## (20180728) 수정사항

1. 호전적인 scv 성질 죽여놓기 > 본진 파란 원 안으로 들어올 때에만 (본진-입구 중간 지점으로) 어택땅
2. 투혼 정찰맨 좌표 수정
3. 4드론 당했는데 정찰맨이 안 죽고 살아있는 경우 > 수정 필요

## (20180725) 수정사항

1. 투혼 맵에서 저그일 경우 감시 지점 세팅 - 일단 secondChokePoint 앞 쪽으로 둠
2. InformationManager 신규 메소드 생성
    - getClosestUnitFromEnemyBaseLocation() : 적의 본진으로부터 가장 가까운 우리 유닛 반환
    - isEnemyUnitInRadius(Unit)	: 우리 유닛으로부터 일정 거리 내에 적의 존재 여부 반환
3. 일꾼 공격모드를 Attack 에서 Combat으로 수정 > 오히려 진다 ㅠㅠ
4. handleRepairWorkers() 부분 수정 준비 > repair 하는 애들 뽑아내는 법 찾기
5. 4드론으로 연속 공격을 당하면? 이길 수 있는 건가?

## (20180724) 시야에 들어오는 적군이 있는가?

1. 문제 상황 : 적 베이스 기준으로 제일 가까운 우리 유닛을 구하고 > 그 유닛을 기준으로 일정 거리 안에 적 유닛이 있는지 체크
2. 대안
    1. 가장 가까운 유닛 > WorkerManager.getClosestEnemyUnitFromWorker() 참고  
    2. 일정 거리 안에 적 유닛 존재 여부 체크 > getUnitsInRadius 활용 > radius의 단위 문제
    3. Config.DrawMapGrid = true 로 하면 타일 체크 가능
    4. 일단 tilePostion 기준 반경 8 tile 이내면 시야인것으로 보인다. 반경 10 tile 정도가 거의 화면 전체
    5. 일정 거리는 8 tile로 해서 8 * Config.TILE_SIZE 이내로 해서 찾으면 될 것 같다.
3. 코딩은 내일 해야겠다.

## (20180721)

1. scv 원하는 위치로 어택땅 가능 > 위치를 지정할 수 있도록 변경할 필요?
2. 드랍 상황 테스트 아직 못 한 상태

## (20180718) 4드론 관련 처리 추가

1. InformationManager - isEmergency 변수 추가, 세팅 로직 추가
2. WorkerData - Job이 Attack인 경우의 setWorkerJob 메소드 추가
3. WorkerManager - 4드론 공격시, scv 8기 공격 태세 전환, Repair 관련 조정 안되도록 설정(? 맞나 모르겠다)
4. 테스트는 아직 못하였습니다 ㅠㅠ 에러나면 수정 부탁드려요.

## (20180717) scv 리밸런싱 수정 완료 및 4드론 관련 처리

1. scv가 공격당했을 경우 or 본진에 적들이 왔을 경우 무조건 도망가도록 처리 - continue 처리
2. 4드론 관련 처리
    - InformationManager 에 isZerglingInMainBaseLocation() 메서드 생성
      + 본진 Region에 저글링 존재 여부 확인
      + update() 메서드에서 1초에 한번, 2분 45초 전에만 체크하게 함
      + 변수를 어디에 어떻게 둘까?
      + strategyManager에서도 위험도 체크를 InformationManager에서 하게 해서 공동으로 사용할 수 있게 하면 안되나?

## (20180712) scv 리밸런싱 관련 처리 수정

1. 문제 상황 
    - 이동이 제대로 안됨 > job을 Move로 지정해줄 경우 미네랄 position으로 목적지를 설정해 줘야함
    - repair, build 할 때 > Move, Idle 에 할당된 scv 골라서 job 부여
    - 지역 위험도 체크가 잘 안되고 있는 상태 - 유닛 리스트를 제대로 못 받아 오는 것 같다

2. 해결 방법
    - RunAway라는 job 새로 생성하여 공격 당할 경우 해당 job 부여
    - 이 경우, 다른 진영에 있는 mineral 하나를 골라서 이동 > CommandUtil.rightClick 활용
    - 이렇게 할 경우 다른 job으로 빠질 일은 없어짐
    - 도착해서 미네랄을 캐고 있으면 다시 미네랄 워커로 세팅

3. 보완점
    - 아직 지역위험도 체크는 다시 확인을 못함 ㅠ_ㅠ

## (20180711) 사람 vs 봇

1. BasicBot 소스를 Export
    - Runnable JAR File 선택
    - Launch Configuration : Main - BasicBot
    - Export destination : 위치 무관, 주소에 영어만 있게할 것
    - Library handling : Extract required libraries into generated JAR
2. 생성된 jar 파일 위치에서 cmd 실행 > java -jar BasicBot.jar 입력(파일명은 각자 다를 수 있음)
3. ChaosLauncher 반드시!!!! 관리자 권한으로 실행
4. 나머지는 위키에서 시키는 대로 하면 대결 가능
5. 뭔가 잘 안된다면 마음을 가라앉히고 다시 처음부터 해봅시다!

6. 내 위험도 체크는 잘못 만들어진 것 같다 ㅠㅠ 뭔가 체크를 이상하게 하고 있는 것 같으니 내일 확인해야겠다.

## (20180710) scv 리밸런싱 관련 처리 완료

1. WorkerData에서 workerDangerMap 및 관련 로직 삭제 - 불필요
2. 리밸런싱 과정에서 미네랄 Worker로 일할 커맨드 센터를 선택할 때, 커맨드 센터가 위치한 곳의 위험도를 체크하여 그 지역은 건너뛰도록 설정
    - InformationManager.Instance().isLocationDangerous 함수 사용
3. 공격받았을 경우에 대한 처리: 멀티 여러개일 경우 반영하여 수정
    1. 본진에서 공격받을 경우 > 첫번째 멀티로 이동
    2. 첫번째 멀티에서 공격받을 경우 > 본진으로 이동
    3. n번째 멀티에서 공격받을 경우 > n-1번째 멀티로 이동

## (20180709) InformationManager 수정

1. 각 진영에 쳐들어온 적군의 정보를 담는 Vector 추가 - mainBaseEnemyInfo / expansionEnemyInfo(Vector의 Map)
2. baseLocation의 위험도 체크를 위한 과정
    1. update() - updateOccupiedLocationUnitsInfo() 호출
    2. updateOccupiedLocationUnitsInfo()
        - 각 occupied BaseLocation에 대해 적군 unit 정보를 업데이트 하여 Vector에 저장
        - getNearbyForce(...) 호출
        - mainBaseEnemyInfo / expansionEnemyInfo 에 적군의 정보 저장되도록 호출
    3. getNearbyForce(...)
        - 해당 Player의 position 주위의 유닛 목록을 unitInfo 에 저장
    4. isLocationDangerous(baseLocation)
        - 해당 Location의 위험도 체크하는 함수
        - mainBaseEnemyInfo / expansionEnemyInfo 이용하여 적군의 수가 5 이상이면 true 반환
    
## (20180706) scv 리밸런싱 대안

1. scv가 도망쳐 온 곳이 위험한지 체크하는 자료구조 만들기 - Map<Integer, Boolean>
2. 도망쳐갈 때 boolean 값을 true로 놓기
3. 매순간 도망쳐온 곳이 위험한지 체크 > 적군의 수가 적으면 위험 false 처리

## (20180705) scv 리밸런싱 문제 해결 계속

- 어떻게 하면 scv가 공격받는 지역으로 되돌아 가지 않을까? 
1. 해당 지역의 커맨드 센터가 공격받는지 여부 확인? 
    - 순간적으로 공격받지 않을 수도 있고, 지역 전체에 대한 정보는 될 수 없음

2. 해당 지역 내에 상대편 유닛이 얼마나 많은지 확인?
    - getUnitsInRectangle / getUnitsInRadius 이용하여 해당 지역 내의 상대 유닛이 몇 마리나 있는지 확인 가능
    - 많고 적음의 기준이 필요함, 일단 5 이상으로 둔 상태 
    - 그냥 한마리만 있으면 안 와도 되는 걸까?

3. 왜 나에겐 적이 쳐들어오는 상황이 안나오는걸까.. ^_^

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
 