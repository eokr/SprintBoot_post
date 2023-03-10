package com.practice.board.service;

import com.practice.board.dto.PaginationDto;
import com.practice.board.dto.PostRequestDto;
import com.practice.board.dto.PostResponseDto;
import com.practice.board.entity.Post;
import com.practice.board.exception.CustomException;
import com.practice.board.exception.ErrorCode;
import com.practice.board.repository.PostRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostServiceImpl {

    private final PostRepository postRepository;

    /**
     * 포스트 생성
     */
    // 추가, 갱신, 삭제등의 작업을 할 때 오류가 발생하면 원상태로 되돌릴 수 있음
    @Transactional
    public Long save(final PostRequestDto params) {
        Post entity = postRepository.save(params.toEntity());
        return entity.getId();
    }

    /**
     * 포스트 리스트 조회
     */
    public List<PostResponseDto> findAll() {
        //최근 포스트 부터 보이도록 내림차순으로 정렬
        Sort sort = Sort.by(Sort.Direction.DESC, "id", "createdDate");
        List<Post> list = postRepository.findAll(sort);
        return list.stream().map(PostResponseDto::new).toList();
    }

    /**
     * 포스트 페이지네이션으로 조회
     */
    public Page<PostResponseDto> findPageByAll(String page, int recodeSize) {
        //최근 포스트 부터 보이도록 내림차순으로 정렬
        Sort sort = Sort.by(Sort.Direction.DESC, "id", "createdDate");

        Page<Post> postPage = postRepository.findPageByAll(PageRequest.of(Integer.parseInt(page) - 1, recodeSize, sort));

        return postPage.map(PostResponseDto::new);
    }

    /**
     * 포스트 페이지네이션 관련 정보가 있는 Dto 반환
     */
    public PaginationDto getPaginationDto(String page, int recodeSize, int pageSize) {
        PaginationDto paginationDto = new PaginationDto();
        paginationDto.setPage(Integer.parseInt(page));
        paginationDto.setTotalPage(findTotalPage(recodeSize));
        paginationDto.setRecodeSize(recodeSize);
        paginationDto.setStartPage(((Integer.parseInt(page) - 1)/pageSize)*pageSize + 1);
        paginationDto.setLastPage((paginationDto.getStartPage() + (pageSize - 1)) < paginationDto.getTotalPage() ? (paginationDto.getStartPage() + (pageSize - 1)) : paginationDto.getTotalPage());
        paginationDto.setPageSize(pageSize);
        return paginationDto;
    }

    /**
     * 포스트의 전체 페이지 개수 조회
     */
    public int findTotalPage(int recodeSize) {
        //삭제여부 false 인 행 갯수 가져오기
        int count = postRepository.CountByDeleteYesOrNo(false).intValue();
        return (count % recodeSize == 0 ? count / recodeSize : (count / recodeSize) + 1);
    }

    /**
     * 포스트 id로 조회
     */
    public PostResponseDto findById(Long id) {
        Post post = postRepository.findById(id).orElseThrow(() -> new CustomException(ErrorCode.POSTS_NOT_FOUND));
        return new PostResponseDto(post);
    }

    /**
     * 포스트 수정
     */
    @Transactional
    public Long update(final Long id, final PostRequestDto params) {

        Post entity = postRepository.findById(id).orElseThrow(() -> new CustomException(ErrorCode.POSTS_NOT_FOUND));
        entity.update(params.getTitle(), params.getContent(), params.getWriter());
        return id;
    }

    /**
     * 조회수 수정
     */
    @Transactional
    public int updateViewCount(Long idPost, HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        boolean checkCookie = false;
        int result = 0;
        if(cookies != null) {
            for(Cookie cookie : cookies) {
                // 이미 조회를 한 경우 체크
                if(cookie.getName().equals("VIEWCOUNT" + idPost)) {
                    checkCookie = true;
                }
            }
            if(!checkCookie) {
                Cookie newCookie = createCookieForNotOverlap(idPost);
                response.addCookie(newCookie);
                result = postRepository.updatePlusOneViewCount(idPost);
            }
        } else {
            Cookie newCookie = createCookieForNotOverlap(idPost);
            response.addCookie(newCookie);
            result = postRepository.updatePlusOneViewCount(idPost);
        }
        return result;
    }

    /**
     * 조회수 중복 방지를 위한 쿠키 생성
     */
    private Cookie createCookieForNotOverlap(Long idPost) {
        Cookie cookie = new Cookie("VIEWCOUNT" + idPost, String.valueOf(idPost));
        cookie.setMaxAge(getRemainSecondForTomorrow());
        cookie.setHttpOnly(true);
        return cookie;
    }

    /**
     * 다음날 정각까지 남은 시간(초)
     */
    private int getRemainSecondForTomorrow() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1L).truncatedTo(ChronoUnit.DAYS);
        return (int) now.until(tomorrow, ChronoUnit.SECONDS);
    }
}