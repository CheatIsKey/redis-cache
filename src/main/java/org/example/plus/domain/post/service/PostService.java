package org.example.plus.domain.post.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.plus.common.entity.Post;
import org.example.plus.common.entity.User;
import org.example.plus.domain.post.model.dto.PostDto;
import org.example.plus.domain.post.model.dto.PostSummaryDto;
import org.example.plus.domain.post.model.request.UpdatePostRequest;
import org.example.plus.domain.post.repository.PostRepository;
import org.example.plus.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostCacheService postCacheService;

    @Transactional
    public PostDto creatPost(String username, String content) {

        User user = userRepository.findUserByUsername(username).orElseThrow(
            ()-> new IllegalArgumentException("등록된 사용자가 없습니다.")
        );

        Post post = postRepository.save(new Post(content, user.getId()));

        return PostDto.from(post);

    }


    // 지연 로딩이구나
    // 실질적으로 사용할 때 불러오는 것이구나!

    // 즉시 로딩으로 한번 테스트를 진행해보겠습니다!
    // 유저를 조회 하자 마자 조회를 할때 연관된 모든 것들을 싸그리 싹싹 긁거서 가져올 것이다.

    public List<PostDto> getPostListByUsername(String username) {

 /*       User user = userRepository.findUserByUsername(username).orElseThrow(
            () -> new IllegalArgumentException("등록된 사용자가 없습니다.")
        );

        List<Post> postList = user.getPosts();


        // post List 를 postDto list로 변환 한것이다.
        return postList.stream()
            .map(PostDto::from)
            .collect(Collectors.toList());*/
        return null;
    }

    public List<PostSummaryDto> getPostSummaryListByUsername(String username) {

        List<PostSummaryDto> result = postRepository.findPostSummary(username);
        return result;
    }

    public PostDto getPostById(Long postId) {
        PostDto postCache = postCacheService.getPostCache(postId);

        if (postCache != null) {
            log.info("[Redis Cache HIT] postId: {}", postId);

            // 조회된 Post 조회수 증가
            postCacheService.increaseViewCount(postId);

            return postCache;
        }

        log.info("[Redis Cache MISS] postId: {}", postId);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post가 없습니다."));

        // 조회된 Post 조회수 증가
        postCacheService.increaseViewCount(postId);

        PostDto postDto = PostDto.from(post);

        // redis에 저장
        postCacheService.savePostCache(postId, postDto);

        return postDto;
    }

    @Transactional
    public PostDto updatePostById(Long postId, UpdatePostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post가 없습니다."));

        post.update(request);

        postRepository.save(post);

        // 캐시 삭제
        postCacheService.deletePostCache(postId);

        return PostDto.from(post);
    }

    public List<PostDto> getTopPostList(int limit) {
        List<Long> topPostIdList = postCacheService.getTopPostList(limit);

        if (topPostIdList.isEmpty()) {
            return Collections.emptyList();
        }

        List<PostDto> postDtoList = topPostIdList.stream()
                .map(postId -> Optional.ofNullable(postCacheService.getPostCache(postId))
                        .orElseGet(() -> PostDto.from(postRepository.findById(postId)
                                .orElseThrow(() -> new IllegalArgumentException("Post가 없습니다.")))))
                .toList();

        return postDtoList;
    }
}


