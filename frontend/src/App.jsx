import { BrowserRouter, Routes, Route, Outlet } from "react-router-dom";
import Header from "./components/Header.jsx";
import { ToastProvider } from "./components/Toast.jsx";
import Cart from "./pages/Cart.jsx";
import Checkout from "./pages/Checkout.jsx";

function Layout() {
  return (
    <div className="min-h-screen bg-[var(--color-paper)]">
      <Header cartCount={3} wishlistCount={2} />
      <Outlet />
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <Routes>
          <Route element={<Layout />}>
            {/* ProductList는 오현님 작업 예정 - 지금은 Header만 노출 */}
            <Route path="/" element={null} />
            <Route path="/cart" element={<Cart />} />
            <Route path="/checkout" element={<Checkout />} />
          </Route>
        </Routes>
      </ToastProvider>
    </BrowserRouter>
  );
}
