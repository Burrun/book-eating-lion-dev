import { BrowserRouter, Routes, Route, Outlet } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Header from "./components/Header.jsx";
import { ToastProvider } from "./components/Toast.jsx";
import Cart from "./pages/Cart.jsx";
import Checkout from "./pages/Checkout.jsx";
import MyPage from "./pages/MyPage.jsx";
import ProductListPage from "./pages/ProductListPage/ProductListPage.tsx";
import ProductDetailPage from "./pages/ProductDetailPage/ProductDetailPage.tsx";
import NewReleasesPage from "./pages/NewReleasesPage/NewReleasesPage.tsx";
import BestsellersPage from "./pages/BestsellersPage/BestsellersPage.tsx";
import WishlistPage from "./pages/WishlistPage/WishlistPage.tsx";
import CardsPage from "./pages/CardsPage/CardsPage.tsx";

const queryClient = new QueryClient();

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
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ToastProvider>
          <Routes>
            <Route element={<Layout />}>
              <Route path="/" element={<ProductListPage />} />
              <Route path="/category" element={<ProductListPage />} />
              <Route path="/best" element={<BestsellersPage />} />
              <Route path="/new" element={<NewReleasesPage />} />
              <Route path="/books/:id" element={<ProductDetailPage />} />
              <Route path="/cart" element={<Cart />} />
              <Route path="/checkout" element={<Checkout />} />
              <Route path="/mypage" element={<MyPage />} />
              <Route path="/wishlist" element={<WishlistPage />} />
              <Route path="/cards" element={<CardsPage />} />
            </Route>
          </Routes>
        </ToastProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
