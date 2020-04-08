/*
 *    Copyright 2020 Frederic Thevenet
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package eu.fthevenet.mattermost;

import net.bis5.mattermost.client4.model.ApiError;

public class MattermostApiException extends Exception {
    private final ApiError apiError;

    public MattermostApiException(ApiError apiError) {
        this("Mattermost error: ", apiError);
    }

    public MattermostApiException(String message, ApiError apiError) {
        super(formatApiErrorMessage(message, apiError));
        this.apiError = apiError;
    }

    public MattermostApiException(ApiError apiError, Throwable cause) {
        this("Mattermost error: ", apiError, cause);
    }

    public MattermostApiException(String message, ApiError apiError, Throwable cause) {
        super(formatApiErrorMessage(message, apiError), cause);
        this.apiError = apiError;
    }

    public static String formatApiErrorMessage(String message, ApiError apiError) {
        return String.format("%s: [%s] [%s] (%s)",
                message,
                apiError.getStatusCode(),
                apiError.getMessage(),
                apiError.getDetailedError());
    }

    public ApiError getApiError() {
        return apiError;
    }
}
