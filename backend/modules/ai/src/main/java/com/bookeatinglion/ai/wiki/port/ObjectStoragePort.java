package com.bookeatinglion.ai.wiki.port;

/**
 * 원본 파일 저장소. 구현은 apps/ai-api 에 있다 — {@link VectorSearchPort} 와 같은 이유로
 * 도메인은 S3 를 몰라야 한다.
 *
 * <p>EPUB 한 권이 수백 KB~수 MB 라 통째로 메모리에 올린다. 스트리밍으로 받아도 zip 은
 * 어차피 전체를 읽어야 열린다.
 */
public interface ObjectStoragePort {

    /**
     * @param key 버킷 안의 객체 키. 버킷 이름은 구현체가 설정에서 가진다 — 호출자가 버킷을
     *     고르게 하면 잘못된 버킷을 읽는 경로가 생긴다.
     * @throws IllegalStateException 객체가 없거나 읽을 수 없을 때
     */
    byte[] download(String key);
}
