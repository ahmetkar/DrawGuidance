import SwiftUI

/// v55 iOS 16 uyumu (dağıtım hedefi 17.0 → 16.0). iOS 17'nin iki parametreli `onChange(of:) { eski, yeni in }`
/// biçimi iOS 16'da yok; bu değiştirici iOS 17+'da sistemin kendisini, iOS 16'da eski değeri kendisi tutan
/// eşdeğerini kullanır.
extension View {
    @ViewBuilder
    func onChangeCompat<V: Equatable>(of value: V, initial: Bool = false, _ action: @escaping (_ old: V, _ new: V) -> Void) -> some View {
        if #available(iOS 17.0, *) {
            self.onChange(of: value, initial: initial, action)
        } else {
            self.modifier(OnChangeCompat16(value: value, initial: initial, action: action))
        }
    }

    /// v55 `scrollBounceBehavior` iOS 16.4'te geldi; öncesinde değişiklik yok.
    @ViewBuilder
    func scrollBounceBasedOnSizeCompat() -> some View {
        if #available(iOS 16.4, *) { self.scrollBounceBehavior(.basedOnSize) } else { self }
    }
}

/// iOS 16: `onChange(of:perform:)` yalnız yeni değeri verir; önceki değer burada saklanır.
private struct OnChangeCompat16<V: Equatable>: ViewModifier {
    let value: V
    let initial: Bool
    let action: (V, V) -> Void
    @State private var previous: V?

    func body(content: Content) -> some View {
        content
            .onAppear {
                if previous == nil {
                    previous = value
                    if initial { action(value, value) }
                }
            }
            .onChange(of: value) { new in
                let old = previous ?? new
                previous = new
                action(old, new)
            }
    }
}

/// v55 iOS 16 uyumu: `navigationDestination(item:)` iOS 17'de geldi. İsteğe bağlı değere bağlı bir
/// `isPresented` köprüsüyle aynı davranış.
extension View {
    func navigationDestinationCompat<Item: Hashable, Destination: View>(
        item: Binding<Item?>, @ViewBuilder destination: @escaping (Item) -> Destination
    ) -> some View {
        let presented = Binding<Bool>(
            get: { item.wrappedValue != nil },
            set: { if !$0 { item.wrappedValue = nil } }
        )
        return self.navigationDestination(isPresented: presented) {
            if let value = item.wrappedValue { destination(value) }
        }
    }
}
