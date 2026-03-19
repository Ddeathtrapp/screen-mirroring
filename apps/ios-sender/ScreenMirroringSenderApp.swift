import SwiftUI

@main
struct ScreenMirroringSenderApp: App {
  @StateObject private var viewModel = SenderViewModel()

  var body: some Scene {
    WindowGroup {
      SenderHomeView(viewModel: viewModel)
    }
  }
}
