import { BrowserRouter, Routes, Route, Outlet } from "react-router-dom";
import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import Header from "./components/Header.jsx";
import ChatContainer from "./components/chat/ChatContainer.tsx";
import { ToastProvider } from "./components/Toast.jsx";
import { AuthProvider } from "./context/AuthContext.jsx";
import { getCart } from "./api/cart.js";
import ProtectedRoute from "./components/ProtectedRoute.jsx";
import AdminRoute from "./components/AdminRoute.jsx";
import Login from "./pages/Login.jsx";
import Signup from "./pages/Signup.jsx";
import Cart from "./pages/Cart.jsx";
import Checkout from "./pages/Checkout.jsx";
import MyPage from "./pages/MyPage.jsx";
import ProductListPage from "./pages/ProductListPage/ProductListPage.tsx";
import ProductDetailPage from "./pages/ProductDetailPage/ProductDetailPage.tsx";
import NewReleasesPage from "./pages/NewReleasesPage/NewReleasesPage.tsx";
import BestsellersPage from "./pages/BestsellersPage/BestsellersPage.tsx";
import WishlistPage from "./pages/WishlistPage/WishlistPage.tsx";
import CardsPage from "./pages/CardsPage/CardsPage.tsx";
import AddressesPage from "./pages/AddressesPage/AddressesPage.tsx";
import KakaoPayCallback from "./pages/payment/KakaoPayCallback.jsx";
import KakaoPaySuccess from "./pages/payment/KakaoPaySuccess.jsx";
import KakaoPayFail from "./pages/payment/KakaoPayFail.jsx";
import KakaoPayCancel from "./pages/payment/KakaoPayCancel.jsx";
import AdminPage from "./pages/AdminPage/AdminPage.tsx";
import AdminCategoriesPage from "./pages/AdminPage/AdminCategoriesPage.tsx";
import AdminFaqPage from "./pages/AdminPage/AdminFaqPage.tsx";
import AdminInquiriesPage from "./pages/AdminPage/AdminInquiriesPage.tsx";
import AdminReviewsPage from "./pages/AdminPage/AdminReviewsPage.tsx";
import AdminCouponsPage from "./pages/AdminPage/AdminCouponsPage.tsx";
import AdminOrdersPage from "./pages/AdminPage/AdminOrdersPage.tsx";
import AdminSubscriptionBannersPage from "./pages/AdminPage/AdminSubscriptionBannersPage.tsx";

const queryClient = new QueryClient();

function Layout() {
  // 장바구니 담기/수량변경/삭제/로그인 시 병합 등에서 ["cart"]를 invalidate하면
  // 헤더 뱃지가 자동으로 갱신된다.
  const { data: cart } = useQuery({ queryKey: ["cart"], queryFn: getCart });
  const cartCount = cart?.items?.reduce((sum, item) => sum + item.quantity, 0) ?? 0;

  return (
    <div className="min-h-screen bg-[var(--color-paper)]">
      <Header cartCount={cartCount} wishlistCount={2} />
      <Outlet />
      <ChatContainer />
    </div>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ToastProvider>
          <AuthProvider>
            <Routes>
              <Route element={<Layout />}>
                <Route path="/" element={<ProductListPage />} />
                <Route path="/category" element={<ProductListPage />} />
                <Route path="/best" element={<BestsellersPage />} />
                <Route path="/new" element={<NewReleasesPage />} />
                <Route path="/books/:id" element={<ProductDetailPage />} />
                <Route path="/login" element={<Login />} />
                <Route path="/signup" element={<Signup />} />
                <Route path="/cart" element={<Cart />} />
                <Route
                  path="/checkout"
                  element={
                    <ProtectedRoute>
                      <Checkout />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/mypage"
                  element={
                    <ProtectedRoute>
                      <MyPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/wishlist"
                  element={
                    <ProtectedRoute>
                      <WishlistPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/cards"
                  element={
                    <ProtectedRoute>
                      <CardsPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/addresses"
                  element={
                    <ProtectedRoute>
                      <AddressesPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/payments/kakao/callback"
                  element={
                    <ProtectedRoute>
                      <KakaoPayCallback />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/payment/kakao/success"
                  element={
                    <ProtectedRoute>
                      <KakaoPaySuccess />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/payment/kakao/fail"
                  element={
                    <ProtectedRoute>
                      <KakaoPayFail />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/payment/kakao/cancel"
                  element={
                    <ProtectedRoute>
                      <KakaoPayCancel />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/admin"
                  element={
                    <AdminRoute>
                      <AdminPage />
                    </AdminRoute>
                  }
                />
                <Route
                  path="/admin/categories"
                  element={
                    <AdminRoute>
                      <AdminCategoriesPage />
                    </AdminRoute>
                  }
                />
                <Route
                  path="/admin/faqs"
                  element={
                    <AdminRoute>
                      <AdminFaqPage />
                    </AdminRoute>
                  }
                />
                <Route
                  path="/admin/inquiries"
                  element={
                    <AdminRoute>
                      <AdminInquiriesPage />
                    </AdminRoute>
                  }
                />
                <Route
                  path="/admin/reviews"
                  element={
                    <AdminRoute>
                      <AdminReviewsPage />
                    </AdminRoute>
                  }
                />
                <Route
                  path="/admin/coupons"
                  element={
                    <AdminRoute>
                      <AdminCouponsPage />
                    </AdminRoute>
                  }
                />
                <Route
                  path="/admin/orders"
                  element={
                    <AdminRoute>
                      <AdminOrdersPage />
                    </AdminRoute>
                  }
                />
                <Route
                  path="/admin/subscription-banners"
                  element={
                    <AdminRoute>
                      <AdminSubscriptionBannersPage />
                    </AdminRoute>
                  }
                />
              </Route>
            </Routes>
          </AuthProvider>
        </ToastProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
