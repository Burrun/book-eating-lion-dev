import { Component } from "react";

// 렌더링 중(또는 커밋 단계) 예외를 잡아 앱 전체가 흰 화면이 되는 대신 최소한의 에러 UI만
// 보여준다. 클래스 컴포넌트로만 구현 가능한 React 기능(getDerivedStateFromError)이라
// 함수형으로 못 바꾼다.
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error, info) {
    console.error("[ErrorBoundary]", error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        this.props.fallback ?? (
          <div className="flex flex-col items-center justify-center gap-2 p-10 text-center">
            <p className="text-sm text-[var(--color-ink)] opacity-60">
              화면을 표시하는 중 오류가 발생했어요.
            </p>
          </div>
        )
      );
    }
    return this.props.children;
  }
}
