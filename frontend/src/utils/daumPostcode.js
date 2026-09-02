// Daum 우편번호 서비스 스크립트 로더. Checkout.jsx와 AddressesPage.tsx가 함께 쓴다.
export function loadDaumPostcodeScript() {
  return new Promise((resolve, reject) => {
    if (window.daum?.Postcode) return resolve();
    const script = document.createElement("script");
    script.src = "https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";
    script.onload = resolve;
    script.onerror = reject;
    document.head.appendChild(script);
  });
}

/** oncomplete(data) 콜백에서 { zipcode, address }로 바로 쓸 수 있게 정규화한다. */
export function openDaumPostcode(onComplete) {
  return loadDaumPostcodeScript().then(() => {
    new window.daum.Postcode({
      oncomplete: (data) => {
        onComplete({
          zipcode: data.zonecode,
          address: data.roadAddress || data.jibunAddress,
        });
      },
    }).open();
  });
}
