import { useQuery } from "@tanstack/react-query";
import BookCard from "../../components/BookCard/BookCard.tsx";
import { getNewReleases } from "../../api/books.ts";

export default function NewReleasesPage() {
  const {
    data: books,
    isPending,
    isError,
  } = useQuery({
    queryKey: ["newReleases"],
    queryFn: () => getNewReleases(),
  });

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-6">
      <section className="flex flex-col gap-4 rounded-2xl border border-forest/10 bg-white p-6">
        <h2 className="text-xl font-bold">✨ 신간</h2>
        {isPending ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="skeleton-shimmer h-56 rounded-xl" />
            ))}
          </div>
        ) : isError ? (
          <p className="py-10 text-center text-sm text-coral">
            신간 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.
          </p>
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            {books.map((book) => (
              <BookCard key={book.id} {...book} />
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
