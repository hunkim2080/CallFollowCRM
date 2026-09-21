package com.detailline.callfollowcrm.presentation.qr

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * QR 스캔 화면 — **세로로 고정.** (2026-09-21 사장님 "왜 가로모드지? 버벅대는데")
 *
 * zxing 라이브러리의 기본 화면(CaptureActivity)은 제 매니페스트에
 * `android:screenOrientation="sensorLandscape"` 로 **가로가 박혀 있다.**
 * 그래서 폰을 세로로 들고 있어도 화면이 눕고, 열 때마다 화면이 한 번 돌면서
 * 카메라가 껐다 켜져 **버벅였다.**
 *
 * 고치는 법은 하나뿐이다 — 그 화면을 상속해 **우리 매니페스트에 세로로 다시 선언**하는 것.
 * (라이브러리 매니페스트는 우리가 못 고친다)
 */
class PortraitCaptureActivity : CaptureActivity()
