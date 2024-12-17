package com.hayden.utilitymodule.result.res_support.many.stream;

import lombok.Builder;

@Builder
public record StreamResultOptions(boolean empty, boolean isInfinite, boolean isNonEmpty, boolean hasErr, boolean hasRes) {
}
