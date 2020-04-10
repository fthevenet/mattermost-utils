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

import picocli.CommandLine;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum Console {
    io;
    private boolean verbose;

    public void printMessage(Object value) {
        printMessage(value, true);
    }

    public void printMessage(Object value, boolean addLF) {
        System.out.print(CommandLine.Help.Ansi.AUTO.string(String.format(
                "@|bold,blue %s|@%s",
                value == null ? "null" : value.toString(),
                (addLF ? "\n" : "")))
        );
    }

    public void printValue(Object value) {
        printValue(value, true);
    }

    public void printValue(Object value, boolean addLF) {
        System.out.print(CommandLine.Help.Ansi.AUTO.string(String.format(
                "@|magenta %s|@%s",
                value == null ? "null" : value.toString(),
                (addLF ? "\n" : "")))
        );
    }

    public void printKeyValuePair(String key, Object value) {
        printMessage(key + ": ", false);
        printValue(value);
    }

    public void printError(String error) {
        printError(error, true);
    }

    public void printError(String error, boolean addLF) {
        System.out.print(CommandLine.Help.Ansi.AUTO.string(String.format("@|red %s|@%s", error, (addLF ? "\n" : ""))));
    }

    public void printException(String message, Throwable t) {
        printError(message);
        printError(t.getMessage());
        printDebug(t);
    }

    public void printException(Throwable t) {
        printError(t.getMessage());
        printDebug(t);
    }

    public void printDebug(Object debug) {
        printDebug(debug, true);
    }

    public void printDebug(Object debug, boolean addLF) {
        if (verbose) {
            System.out.print(CommandLine.Help.Ansi.AUTO.string(String.format("@|fg(238) %s|@%s", debug, (addLF ? "\n" : ""))));
        }
    }

    public void printDebug(Throwable t) {
        printDebug(Arrays.stream(t.getStackTrace())
                .map(StackTraceElement::toString)
                .collect(Collectors.joining("\n\tat ", t.toString() + "\n\tat ", "")));
        if (t.getCause() != null) {
            printDebug("Caused by: ", false);
            printDebug(t.getCause());
        }
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isVerbose() {
        return verbose;
    }



}
