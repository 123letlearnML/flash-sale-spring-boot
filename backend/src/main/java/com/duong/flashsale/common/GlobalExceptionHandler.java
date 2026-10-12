package com.duong.flashsale.common;

import com.duong.flashsale.item.ItemNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ItemNotFoundException.class)
    public ProblemDetail handleNotFound(ItemNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid reference (user or item does not exist)");
    }

    /**
     * Quá tải: hết connection trong pool (Hikari timeout), chờ khoá quá lâu hoặc bị chọn làm nạn nhân deadlock.
     * Trả 503 để client/load balancer biết là "thử lại sau", không phải lỗi logic.
     *
     * <p>Hết connection có 2 dạng: lúc mở transaction => CannotCreateTransactionException;
     * lúc query ngoài transaction (vd. bước kiểm tra nhanh) => DataAccessResourceFailureException.
     * TransientDataAccessException bao gồm deadlock, lock wait timeout, query timeout.
     */
    @ExceptionHandler({
            CannotCreateTransactionException.class,
            DataAccessResourceFailureException.class,
            TransientDataAccessException.class
    })
    public ProblemDetail handleOverload(RuntimeException e) {
        log.warn("Overloaded: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Server busy, please retry");
    }
}
