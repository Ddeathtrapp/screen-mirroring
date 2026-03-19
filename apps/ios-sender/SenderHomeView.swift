import SwiftUI

struct SenderHomeView: View {
  @ObservedObject var viewModel: SenderViewModel

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 20) {
          header
          pairingCard
          stateCard
          todoCard
        }
        .padding(20)
      }
      .navigationTitle("Screen Mirroring Sender")
      .navigationBarTitleDisplayMode(.inline)
    }
  }

  private var header: some View {
    VStack(alignment: .leading, spacing: 8) {
      Text("iOS Sender Foundation")
        .font(.system(.largeTitle, design: .rounded).weight(.bold))
      Text("Placeholder shell for the future ReplayKit-based sender.")
        .foregroundStyle(.secondary)
    }
  }

  private var pairingCard: some View {
    VStack(alignment: .leading, spacing: 12) {
      Text("Pairing")
        .font(.headline)

      TextField("Pairing code", text: Binding(
        get: { viewModel.pairingCode },
        set: { viewModel.updatePairingCode($0) }
      ))
      .textInputAutocapitalization(.never)
      .keyboardType(.numberPad)
      .textFieldStyle(.roundedBorder)

      TextField("Sender name", text: $viewModel.senderName)
        .textFieldStyle(.roundedBorder)

      HStack {
        Button("Connect") {
          viewModel.connectTapped()
        }
        .buttonStyle(.borderedProminent)
        .disabled(!viewModel.canConnect)

        Button("Disconnect") {
          viewModel.disconnectTapped()
        }
        .buttonStyle(.bordered)
        .disabled(viewModel.connectionState == .idle || viewModel.connectionState == .readyToConnect)
      }
    }
    .padding(16)
    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
  }

  private var stateCard: some View {
    VStack(alignment: .leading, spacing: 10) {
      Text("Connection State")
        .font(.headline)

      Text(viewModel.connectionState.title)
        .font(.title3.weight(.semibold))

      Text(viewModel.statusMessage)
        .foregroundStyle(.secondary)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(16)
    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
  }

  private var todoCard: some View {
    VStack(alignment: .leading, spacing: 8) {
      Text("Next native work")
        .font(.headline)
      Text("TODO(ReplayKit): add a Broadcast Upload Extension for screen capture.")
      Text("TODO(WebRTC): add sender media publishing and ICE handling.")
      Text("TODO(Signaling): connect this shell to the backend pairing flow.")
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(16)
    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
  }
}
