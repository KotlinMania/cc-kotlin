// port-lint: source src/lib.rs
package io.github.kotlinmania.cc

enum class ErrorKind {
    /** Error occurred while performing I/O. */
    IOError,

    /** Environment variable not found, with the var in question as extra info. */
    EnvVarNotFound,

    /** Error occurred while using external tools (ie: invocation of compiler). */
    ToolExecError,

    /** Error occurred due to missing external tools. */
    ToolNotFound,

    /** One of the function arguments failed validation. */
    InvalidArgument,

    /** No known macro is defined for the compiler when discovering tool family. */
    ToolFamilyMacroNotFound,

    /** Invalid target. */
    InvalidTarget,

    /** Unknown target. */
    UnknownTarget,

    /** Invalid rustc flag. */
    InvalidFlag,

    /** jobserver helpthread failure */
    JobserverHelpThreadError,

    /** `cc` has been disabled by an environment variable. */
    Disabled,
}

/**
 * Represents an internal error that occurred, with an explanation.
 */
class Error(
    val kind: ErrorKind,
    override val message: String,
) : Exception("$kind: $message") {
    override fun toString(): String = "$kind: $message"

    companion object {
        fun new(kind: ErrorKind, message: String): Error = Error(kind, message)
    }
}
