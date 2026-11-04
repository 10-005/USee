import AVFoundation
import React
import React_RCTAppDelegate
import ReactAppDependencyProvider
import UIKit

class SceneDelegate: RCTDefaultReactNativeFactoryDelegate, UIWindowSceneDelegate {
  private var splashPlayer: AVPlayer?
  private var splashOverlay: UIView?
  private var splashObservers: [NSObjectProtocol] = []
  private var splashTimeout: DispatchWorkItem?
  var window: UIWindow?
  var reactNativeFactory: RCTReactNativeFactory?

  func scene(
    _ scene: UIScene,
    willConnectTo session: UISceneSession,
    options connectionOptions: UIScene.ConnectionOptions
  ) {
    guard let windowScene = scene as? UIWindowScene else {
      return
    }

    dependencyProvider = RCTAppDependencyProvider()
    reactNativeFactory = RCTReactNativeFactory(delegate: self)
    window = UIWindow(windowScene: windowScene)
    
    #if DEBUG
    let devMenuConfiguration = RCTDevMenuConfiguration(
      devMenuEnabled: true,
      shakeGestureEnabled: true,
      keyboardShortcutsEnabled: true
    )
    reactNativeFactory?.devMenuConfiguration = devMenuConfiguration
    #endif
    
    reactNativeFactory?.startReactNative(
      withModuleName: "USee",
      in: window,
      connectionOptions: connectionOptions
    )
    showSplash()

   
  }

  private func showSplash() {
    guard let window, let url = Bundle.main.url(forResource: "usee_splash", withExtension: "mp4") else { return }
    let player = AVPlayer(url: url)
    let overlay = SplashVideoView(frame: window.bounds)
    overlay.backgroundColor = UIColor(red: 192/255, green: 214/255, blue: 165/255, alpha: 1)
    overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    overlay.videoLayer.videoGravity = .resizeAspectFill
    overlay.videoLayer.player = player
    player.isMuted = true
    window.addSubview(overlay)
    splashPlayer = player
    splashOverlay = overlay
    for name in [AVPlayerItem.didPlayToEndTimeNotification, AVPlayerItem.failedToPlayToEndTimeNotification] {
      splashObservers.append(NotificationCenter.default.addObserver(
        forName: name, object: player.currentItem, queue: .main
      ) { [weak self] _ in self?.dismissSplash() })
    }
    let timeout = DispatchWorkItem { [weak self] in self?.dismissSplash() }
    splashTimeout = timeout
    DispatchQueue.main.asyncAfter(deadline: .now() + 8, execute: timeout)
    player.play()
  }

  private func dismissSplash() {
    splashTimeout?.cancel()
    splashTimeout = nil
    splashObservers.forEach { NotificationCenter.default.removeObserver($0) }
    splashObservers.removeAll()
    splashPlayer?.pause()
    splashPlayer = nil
    splashOverlay?.removeFromSuperview()
    splashOverlay = nil
  }

  func sceneDidDisconnect(_ scene: UIScene) {
    dismissSplash()
  }

  func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
    RCTLinkingManager.scene(scene, openURLContexts: URLContexts)
  }

  func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
    RCTLinkingManager.scene(scene, continue: userActivity)
  }

  override func bundleURL() -> URL? {
    #if DEBUG
    RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
    #else
    Bundle.main.url(forResource: "main", withExtension: "jsbundle")
    #endif
  }
}

private final class SplashVideoView: UIView {
  override class var layerClass: AnyClass { AVPlayerLayer.self }
  var videoLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}
