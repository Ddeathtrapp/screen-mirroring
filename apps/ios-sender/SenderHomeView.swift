import SwiftUI

struct SenderHomeView: View {
  @ObservedObject var viewModel: SenderViewModel

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 20) {
          header
          backendCard
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
      Text("Native pairing and claim flow only. No ReplayKit or WebRTC publishing yet.")
        .foregroundStyle(.secondary)
    }
  }

  private var backendCard: some View {
    VStack(alignment: .leading, spacing: 12) {
      Text("Backend")
        .font(.headline)

      TextField("Backend URL", text: Binding(
        get: { viewModel.backendBaseURL },
        set: { viewModel.updateBackendURL($0) }
      ))
        .textInputAutocapitalization(.never)
        .keyboardType(.URL)
        .textFieldStyle(.roundedBorder)
    }
    .padding(16)
    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
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
        Button("Claim session") {
          viewModel.claimTapped()
        }
        .buttonStyle(.borderedProminent)
        .disabled(!viewModel.canClaim)

        Button("Clear") {
          viewModel.disconnectTapped()
        }
        .buttonStyle(.bordered)
        .disabled(viewModel.sessionTicket == nil && viewModel.connectionState == .idle)
      }

      if viewModel.connectionState == .claiming {
        ProgressView()
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

      if let session = viewModel.sessionTicket {
        Divider()
        Text("Session ID: \(session.id)")
        Text("Backend state: \(session.state)")
        Text("Pairing code: \(session.pairingCode)")
        if let senderName = session.senderName {
          Text("Sender name: \(senderName)")
        }
        Text("Signaling URL: \(session.signalingURL.absoluteString)")
          .font(.footnote)
          .foregroundStyle(.secondary)
      }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(16)
    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
  }

  private var todoCard: some View {
    VStack(alignment: .leading, spacing: 8) {
      Text("Next native work")
        .font(.headline)
      Text("TODO(Signaling): open a sender WebSocket after claim success and forward session events.")
      Text("TODO(Auth): persist sender token and session state for the next stage.")
      Text("TODO(ReplayKit): add a Broadcast Upload Extension for screen capture.")
      Text("TODO(WebRTC): add sender media publishing and ICE handling.")
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(16)
    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
  }
}
