import SwiftUI

#if canImport(ComposeApp)
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
#endif

struct ContentView: View {
    var body: some View {
        #if canImport(ComposeApp)
        ComposeView()
            .ignoresSafeArea()
        #else
        NativeSimpMusicView()
            .ignoresSafeArea()
        #endif
    }
}
