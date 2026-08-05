import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import BookCard from "../../components/BookCard/BookCard.tsx";
import { getWishlist, removeFromWishlist } from "../../api/wishlist.ts";
import type { BookSummary } from "../../types/book.ts";

const WISHLIST_QUERY_KEY = ["wishlist"];

export default function WishlistPage() {
  const queryClient = useQueryClient();

  const {
    data: books,
    isPending,
    isError,
  } = useQuery({
    queryKey: WISHLIST_QUERY_KEY,
    queryFn: getWishlist,
  });

  // 삭제 성공 시 목록을 무효화해 재조회한다.
  const { mutate: remove, variables: removingId } = useMutation({
    mutationFn: removeFromWishlist,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: WISHLIST_QUERY_KEY }),
  });

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="flex flex-col gap-4 rounded-2xl border border-forest/10 bg-white p-6">
        <div className="flex items-baseline justify-between">
          <h1 className="text-xl font-bold">❤️ 찜한 책</h1>
          {books ? <p className="text-sm text-forest/60">{books.length}권</p> : null}
        </div>

        <WishlistBody
          books={books}
          isPending={isPending}
          isError={isError}
          removingId={removingId}
          onRemove={remove}
        />
      </section>
    </main>
  );
}

interface WishlistBodyProps {
  books: BookSummary[] | undefined;
  isPending: boolean;
  isError: boolean;
  removingId: number | string | undefined;
  onRemove: (bookId: string) => void;
}

function WishlistBody({ books, isPending, isError, removingId, onRemove }: WishlistBodyProps) {
  if (isPending) {
    return (
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="skeleton-shimmer h-56 rounded-xl" />
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <p className="py-10 text-center text-sm text-coral">
        찜 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.
      </p>
    );
  }

  if (!books || books.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3 py-12">
        <p className="text-4xl">🤍</p>
        <p className="text-sm text-forest/60">아직 찜한 책이 없어요.</p>
        <Link
          to="/"
          className="rounded-full bg-forest px-5 py-2 text-sm font-semibold text-paper transition hover:bg-forest-light"
        >
          책 구경하러 가기 &gt;
        </Link>
      </div>
    );
  }

  return (
    <ul className="grid grid-cols-2 gap-4 sm:grid-cols-4">
      {books.map((book) => (
        <li key={book.id} className="relative">
          <BookCard {...book} />
          <button
            type="button"
            onClick={() => onRemove(book.id)}
            disabled={String(removingId) === book.id}
            aria-label={`${book.title} 찜 해제`}
            className="absolute right-2 top-2 rounded-full bg-white/90 px-2 py-1 text-sm shadow transition hover:bg-white disabled:opacity-40"
          >
            {String(removingId) === book.id ? "⏳" : "✕"}
          </button>
        </li>
      ))}
    </ul>
  );
}
