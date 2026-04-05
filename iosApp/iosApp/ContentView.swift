import SwiftUI
// import shared  // Uncomment after KMM module is built on macOS

struct ContentView: View {
    var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                Image(systemName: "car.fill")
                    .resizable()
                    .frame(width: 80, height: 60)
                    .foregroundColor(.green)

                Text("CarCost")
                    .font(.largeTitle)
                    .fontWeight(.bold)

                Text("iOS версия в разработке")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                Text("Используется KMM (Kotlin Multiplatform Mobile)\nдля переиспользования бизнес-логики")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }
            .navigationTitle("CarCost")
        }
    }
}

#Preview {
    ContentView()
}
