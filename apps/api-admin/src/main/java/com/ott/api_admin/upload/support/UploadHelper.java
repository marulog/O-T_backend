package com.ott.api_admin.upload.support;

import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import com.ott.common.core.response.PageMetadata;
import com.ott.common.core.response.PageResult;
import com.ott.domain.member.domain.Member;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.infra.s3.service.S3PresignService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Component
public class UploadHelper {

    // 5mb s3에서 정한 각 파트 최소 크기( 마지막 파트 제외 )
    private static final long MIN_MULTIPART_PART_SIZE_BYTES = 5L * 1024L * 1024L;
    // 5gb s3에서 정한 각 파트 최대 크기
    private static final long MAX_MULTIPART_PART_SIZE_BYTES = 5L * 1024L * 1024L * 1024L;

    private final MemberRepository memberRepository;
    private final S3PresignService s3PresignService;

    // 파트의 기본 크기
    @Value("${aws.s3.multipart-default-part-size-bytes:16777216}")
    private long multipartDefaultPartSizeBytes;

    // 최대 파트 갯수
    @Value("${aws.s3.multipart-max-parts:2000}")
    private int multipartMaxParts;

    public String buildObjectKey(String resourceRoot, Long resourceId, String assetType, String fileName) {
        return resourceRoot + "/" + resourceId + "/" + assetType + "/" + fileName;
    }

    public String resolveImageContentType(String fileName) {
        try {
            return ExtensionEnum.resolveImageContentType(fileName);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_EXTENSION);
        }
    }

    public String resolveVideoContentType(String fileName) {
        try {
            return ExtensionEnum.resolveVideoContentType(fileName);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_VIDEO_EXTENSION);
        }
    }

    //업로드할 파일명 정규화
    public String sanitizeFileName(String fileName) {
        String trimmed = fileName.trim();
        int extensionDelimiterIndex = trimmed.lastIndexOf('.');
        String baseName = extensionDelimiterIndex > 0 ? trimmed.substring(0, extensionDelimiterIndex) : trimmed;
        String extensionPart = extensionDelimiterIndex > 0 ? trimmed.substring(extensionDelimiterIndex + 1) : "";

        String sanitizedBaseName = baseName
                .replace("/", "")
                .replace("\\", "")
                .replaceAll("[^0-9A-Za-z가-힣_-]", "");
        String sanitizedExtension = extensionPart.replaceAll("[^0-9A-Za-z]", "").toLowerCase();

        if (sanitizedBaseName.isBlank()) {
            sanitizedBaseName = "file";
        }
        if (sanitizedExtension.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_EXTENSION);
        }
        return sanitizedBaseName + "." + sanitizedExtension;
    }

    public Member resolveUploader(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public UploadFileResult createImageUpload(
            String resourceRoot,
            Long resourceId,
            String assetType,
            String fileName
    ) {
        String sanitizedFileName = sanitizeFileName(fileName);
        String objectKey = buildObjectKey(resourceRoot, resourceId, assetType, sanitizedFileName);
        String contentType = resolveImageContentType(sanitizedFileName);
        String objectUrl = s3PresignService.toObjectUrl(objectKey);
        String uploadUrl = s3PresignService.createPutPresignedUrl(objectKey, contentType);
        return new UploadFileResult(objectKey, objectUrl, uploadUrl);
    }

    public UploadFileResult createImageUploadOptional(
            String resourceRoot,
            Long resourceId,
            String assetType,
            String fileName
    ) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        return createImageUpload(resourceRoot, resourceId, assetType, fileName);
    }

    public String buildMasterPlaylistObjectKey(String resourceRoot, Long resourceId) {
        return resourceRoot + "/" + resourceId + "/transcoded/master.m3u8";
    }

    public String toObjectUrl(String objectKey) {
        return s3PresignService.toObjectUrl(objectKey);
    }

    public void validateOriginObjectKey(String objectKey, String expectedOriginUrl, ErrorCode mismatchErrorCode) {
        if (!StringUtils.hasText(objectKey) || !StringUtils.hasText(expectedOriginUrl)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String requestOriginUrl = toObjectUrl(objectKey);
        if (!requestOriginUrl.equals(expectedOriginUrl)) {
            throw new BusinessException(mismatchErrorCode);
        }
    }

    public ImageCreateUploadResult prepareImageCreate(
            String resourceRoot,
            Long resourceId,
            String posterFileName,
            String thumbnailFileName
    ) {
        UploadFileResult posterUpload = createImageUpload(resourceRoot, resourceId, "poster", posterFileName);
        UploadFileResult thumbnailUpload = createImageUpload(resourceRoot, resourceId, "thumbnail", thumbnailFileName);

        return new ImageCreateUploadResult(
                posterUpload.objectKey(),
                thumbnailUpload.objectKey(),
                posterUpload.objectUrl(),
                thumbnailUpload.objectUrl(),
                posterUpload.uploadUrl(),
                thumbnailUpload.uploadUrl()
        );
    }

    public ImageUpdateUploadResult prepareImageUpdate(
            String resourceRoot,
            Long resourceId,
            String posterFileName,
            String thumbnailFileName,
            String currentPosterUrl,
            String currentThumbnailUrl
    ) {
        UploadFileResult posterUpload = createImageUploadOptional(resourceRoot, resourceId, "poster", posterFileName);
        UploadFileResult thumbnailUpload = createImageUploadOptional(resourceRoot, resourceId, "thumbnail", thumbnailFileName);

        String finalPosterUrl = currentPosterUrl;
        String finalThumbnailUrl = currentThumbnailUrl;
        String posterObjectKey = null;
        String thumbnailObjectKey = null;
        String posterUploadUrl = null;
        String thumbnailUploadUrl = null;

        if (posterUpload != null) {
            finalPosterUrl = posterUpload.objectUrl();
            posterObjectKey = posterUpload.objectKey();
            posterUploadUrl = posterUpload.uploadUrl();
        }
        if (thumbnailUpload != null) {
            finalThumbnailUrl = thumbnailUpload.objectUrl();
            thumbnailObjectKey = thumbnailUpload.objectKey();
            thumbnailUploadUrl = thumbnailUpload.uploadUrl();
        }

        return new ImageUpdateUploadResult(
                posterObjectKey,
                thumbnailObjectKey,
                posterUploadUrl,
                thumbnailUploadUrl,
                finalPosterUrl,
                finalThumbnailUrl
        );
    }

    public MediaCreateUploadResult prepareMediaCreate(
            String resourceRoot,
            Long resourceId,
            String posterFileName,
            String thumbnailFileName,
            String originFileName,
            Integer originFileSizeKb
    ) {
        UploadFileResult posterUpload = createImageUpload(resourceRoot, resourceId, "poster", posterFileName);
        UploadFileResult thumbnailUpload =
                thumbnailFileName == null ? null : createImageUpload(resourceRoot, resourceId, "thumbnail", thumbnailFileName);
        MultipartUploadFileResult originUpload = createVideoMultipartUpload(
                resourceRoot,
                resourceId,
                originFileName,
                originFileSizeKb
        );

        String masterPlaylistObjectKey = buildMasterPlaylistObjectKey(resourceRoot, resourceId);
        String masterPlaylistObjectUrl = toObjectUrl(masterPlaylistObjectKey);

        return new MediaCreateUploadResult(
                posterUpload.objectKey(),
                thumbnailUpload == null ? null : thumbnailUpload.objectKey(),
                originUpload.objectKey(),
                masterPlaylistObjectKey,
                posterUpload.objectUrl(),
                thumbnailUpload == null ? null : thumbnailUpload.objectUrl(),
                originUpload.objectUrl(),
                masterPlaylistObjectUrl,
                posterUpload.uploadUrl(),
                thumbnailUpload == null ? null : thumbnailUpload.uploadUrl(),
                originUpload.uploadId(),
                originUpload.totalPartCount(),
                originUpload.partSizeBytes()
        );
    }

    public PageResult<MultipartUploadPartUrl> getMultipartPartUrls(
            String objectKey,
            String uploadId,
            int totalPartCount,
            int page,
            int size
    ) {
        if (!StringUtils.hasText(objectKey) || !StringUtils.hasText(uploadId) || totalPartCount <= 0 || page < 0 || size <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        int totalPage = (totalPartCount + size - 1) / size;
        if (page >= totalPage) {
            return PageResult.of(PageMetadata.of(page, totalPage, size), List.of());
        }

        int startPartNumber = (page * size) + 1;
        int endPartNumber = Math.min(startPartNumber + size - 1, totalPartCount);

        // 페이징(start - end)범위의 url 생성
        List<MultipartUploadPartUrl> dataList = buildMultipartPartUploadUrls(objectKey, uploadId, startPartNumber, endPartNumber);

        return PageResult.of(
                PageMetadata.of(page, totalPage, size),
                dataList
        );
    }

    public void completeMultipartUpload(
            String objectKey,
            String uploadId,
            int totalPartCount,
            List<MultipartPartETag> partETags
    ) {
        // 1) 입력값 기본 유효성 검증
        if (!StringUtils.hasText(objectKey)
                || !StringUtils.hasText(uploadId)
                || totalPartCount <= 0
                || partETags == null
                || partETags.isEmpty()) {
            throw new BusinessException(ErrorCode.ETAG_LIST_INVALID);
        }

        List<MultipartPartETag> normalizedParts = partETags.stream()
                .sorted(Comparator.comparingInt(MultipartPartETag::partNumber))
                .toList();

        // 2) 전달된 ETag 개수가 기대 파트 개수와 일치하는지 검증
        if (normalizedParts.size() != totalPartCount) {
            throw new BusinessException(ErrorCode.ETAG_LIST_INVALID);
        }

        // 3) 각 파트의 번호 범위(1..totalPartCount), ETag 값, 중복 여부를 한 번에 검증
        Set<Integer> seenPartNumbers = new HashSet<>();
        normalizedParts.forEach(part -> {
            if (part.partNumber() < 1
                    || part.partNumber() > totalPartCount
                    || !StringUtils.hasText(part.eTag())
                    || !seenPartNumbers.add(part.partNumber())) {
                throw new BusinessException(ErrorCode.ETAG_LIST_INVALID);
            }
        });

        s3PresignService.completeMultipartUpload(
                objectKey,
                uploadId,
                normalizedParts.stream()
                        .map(part -> new S3PresignService.MultipartPartETag(part.partNumber(), part.eTag()))
                        .toList()
        );
    }

    /**
     * s3에 생긴 멀티파트 업로드 세션을 제거합니다.
     */
    public void abortMultipartUpload(String objectKey, String uploadId) {
        if (!StringUtils.hasText(objectKey) || !StringUtils.hasText(uploadId)) {
            return;
        }
        s3PresignService.abortMultipartUpload(objectKey, uploadId);
    }

    public int getMultipartPartCount(Integer fileSizeKb) {
        return getMultipartPlan(fileSizeKb).totalPartCount();
    }

    /**
     * 5mb, (파일크기/2000), 고정크기(16mb) 중 가장 큰것 선정
     * <p>
     * 5mb : s3에서 정한 최소치<br><br>
     * (파일크기/2000) : 파일이 너무커서 16mb로 2000번 전송해도 부족<br>->파트 갯수 2000개로 한 파트 크기 계산<br><br>
     * 고정 크기 : 적당한 용량의 파일 -> 파트 크기 16mb로 고정(파트 갯수가 줄어듬)
     * </p>
     */
    public MultipartUploadPlan getMultipartPlan(Integer fileSizeKb) {
        if (multipartDefaultPartSizeBytes <= 0 || multipartMaxParts <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (fileSizeKb == null || fileSizeKb <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        long fileSizeBytes = fileSizeKb.longValue() * 1024L;

        //파일을 2000개로 분할 시 한 파트의 크기
        long minPartSizeByMaxParts = ceilDiv(fileSizeBytes, multipartMaxParts);


        long partSizeBytes = Math.max(
                MIN_MULTIPART_PART_SIZE_BYTES,
                Math.max(minPartSizeByMaxParts, multipartDefaultPartSizeBytes)
        );

        // 파일 크기/2000 이 5기가 넘음 -> s3멀티파트 업로드 불가
        if (partSizeBytes > MAX_MULTIPART_PART_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일 크기가 너무 큽니다.");
        }

        // 위에서 정해진 파트 사이즈로 파트 갯수 계산
        int totalPartCount = (int) ceilDiv(fileSizeBytes, partSizeBytes);
        if (totalPartCount <= 0 || totalPartCount > multipartMaxParts) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        return new MultipartUploadPlan(totalPartCount, partSizeBytes);
    }

    private MultipartUploadFileResult createVideoMultipartUpload(
            String resourceRoot,
            Long resourceId,
            String fileName,
            Integer fileSizeKb
    ) {
        String sanitizedFileName = sanitizeFileName(fileName);
        String objectKey = buildObjectKey(resourceRoot, resourceId, "origin", sanitizedFileName);
        String contentType = resolveVideoContentType(sanitizedFileName);
        String objectUrl = s3PresignService.toObjectUrl(objectKey);

        MultipartUploadPlan multipartUploadPlan = getMultipartPlan(fileSizeKb);
        String uploadId = s3PresignService.createMultipartUpload(objectKey, contentType);

        return new MultipartUploadFileResult(
                objectKey,
                objectUrl,
                uploadId,
                multipartUploadPlan.totalPartCount(),
                multipartUploadPlan.partSizeBytes()
        );
    }

    private List<MultipartUploadPartUrl> buildMultipartPartUploadUrls(
            String objectKey,
            String uploadId,
            int startPartNumber,
            int endPartNumber
    ) {
        return IntStream.rangeClosed(startPartNumber, endPartNumber)
                .mapToObj(partNumber -> new MultipartUploadPartUrl(
                        partNumber,
                        s3PresignService.createUploadPartPresignedUrl(objectKey, uploadId, partNumber)
                ))
                .toList();
    }

    private long ceilDiv(long numerator, long denominator) {
        return (numerator + denominator - 1L) / denominator;
    }

    public record UploadFileResult(
            String objectKey,
            String objectUrl,
            String uploadUrl
    ) {
    }

    public record MultipartUploadFileResult(
            String objectKey,
            String objectUrl,
            String uploadId,
            int totalPartCount,
            long partSizeBytes
    ) {
    }

    public record MultipartUploadPlan(
            int totalPartCount,
            long partSizeBytes
    ) {
    }

    public record MultipartUploadPartUrl(
            int partNumber,
            String uploadUrl
    ) {
    }

    public record MultipartPartETag(
            int partNumber,
            String eTag
    ) {
    }

    public record ImageCreateUploadResult(
            String posterObjectKey,
            String thumbnailObjectKey,
            String posterObjectUrl,
            String thumbnailObjectUrl,
            String posterUploadUrl,
            String thumbnailUploadUrl
    ) {
    }

    public record ImageUpdateUploadResult(
            String posterObjectKey,
            String thumbnailObjectKey,
            String posterUploadUrl,
            String thumbnailUploadUrl,
            String nextPosterUrl,
            String nextThumbnailUrl
    ) {
    }

    public record MediaCreateUploadResult(
            String posterObjectKey,
            String thumbnailObjectKey,
            String originObjectKey,
            String masterPlaylistObjectKey,
            String posterObjectUrl,
            String thumbnailObjectUrl,
            String originObjectUrl,
            String masterPlaylistObjectUrl,
            String posterUploadUrl,
            String thumbnailUploadUrl,
            String originUploadId,
            int originTotalPartCount,
            long originPartSizeBytes
    ) {
    }
}
