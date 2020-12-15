# ARcore-Rock-Paper-Scissor-Game
AR(증강현실)로 만들어진 컴퓨터와 가위바위보 게임

## Demo
<img src="images/demo.gif">
"가위, 바위, 보!"라는 음성에 맞춰 손을 바꾸면 인식하여 승부를 판단한다.

## 기술
### 동작 방식
<img src="images/design.png">

### AR(증강현실)
Google ARCore를 활용하여 평면인식 후 객체를 렌더링 한다.

### 객체인식
가위, 바위, 보 모양의 손 데이터 셋을 학습시킨 Tiny YOLOv2 모델을 사용하여 손을 인식한다.

----

## 자랑하고 싶은 점
- 직접 가위바위보 인식 모델을 학습시키고 만들었다.
- 인식 모델은 안드로이드의 백그라운드에서 동작한다.
- 오픈소스(YOLOv2, ARCore)를 혼합, 응용하였다.
