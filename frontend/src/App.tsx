import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ProductListPage from './pages/ProductListPage/ProductListPage.js'
import ProductDetailPage from './pages/ProductDetailPage/ProductDetailPage.js'
import NewReleasesPage from './pages/NewReleasesPage/NewReleasesPage.js'
import BestsellersPage from './pages/BestsellersPage/BestsellersPage.js'

const queryClient = new QueryClient()

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<ProductListPage />} />
          <Route path="/books/:id" element={<ProductDetailPage />} />
          <Route path="/new-releases" element={<NewReleasesPage />} />
          <Route path="/bestsellers" element={<BestsellersPage />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
