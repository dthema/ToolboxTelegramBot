import dev.inmo.micro_utils.coroutines.subscribeSafelyWithoutExceptions
import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.answers.answer
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.chat.modify.setChatMenuButton
import dev.inmo.tgbotapi.extensions.api.edit.edit
import dev.inmo.tgbotapi.extensions.api.send.*
import dev.inmo.tgbotapi.extensions.behaviour_builder.*
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.*
import dev.inmo.tgbotapi.extensions.utils.types.buttons.*
import dev.inmo.tgbotapi.extensions.utils.withContent
import dev.inmo.tgbotapi.requests.abstracts.asMultipartFile
import dev.inmo.tgbotapi.requests.send.media.SendDocument
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.MenuButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.types.queries.callback.MessageDataCallbackQuery
import dev.inmo.tgbotapi.utils.*
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.*
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels

private const val DELIMITER = ":"

private const val WIN_64 = "Windows x64"
private const val WIN_ARM64 = "Windows ARM64"
private const val MAC = "Mac"
private const val MAC_M = "Mac Apple Silicon"
private const val LIN_86_64 = "Linux x86_64"
private const val LIN_AARCH64 = "Linux aarch64"

private const val IDEA = "IntelliJ IDEA"
private const val IDEA_CE = "$IDEA Community Edition"
private const val IDEA_UE = "$IDEA Ultimate Edition"

fun String.parsePlatformAndIde(): Pair<String, String>? {
    val (platformString, ideString) = split(DELIMITER).takeIf { it.count() > 1 } ?: return null
    return Pair(platformString, ideString)
}

fun stringFromPlatformAndIde(platform: String, ide: String) = platform + DELIMITER + ide

suspend fun editMenu(
    bot: TelegramBot,
    it: MessageDataCallbackQuery,
    menuText: String,
    keyboardMarkup: InlineKeyboardMarkup?
) {
    bot.edit(
        it.message.withContent<TextContent>() ?: it.let {
            bot.answer(it, "Unsupported message type")
            return
        },
        replyMarkup = keyboardMarkup
    ) {
        regular(menuText)
    }
}

fun downloadIdea(platform: String, ide: String): String {
    val driver = ChromeDriver()
    driver.get("https://www.jetbrains.com/idea/download/other.html")
    var elements: List<WebElement>? = null
    while (elements == null) {
        elements = driver.findElements(By.className("wt-link"))
    }

    println(driver.pageSource)
    var links = driver.pageSource.split("\"wt-link\"")
    links = links.subList(1, links.size - 2)
    val element = links.first { link -> link.contains(platform) }
    val link = element.split("\"")[1]
    val version = element.split(">")[1].split(" ")[0]
    val fileType = element.split("(")[1].split(")")[0]
    val fileName = "$ide $version.$fileType"

    println(link)

    if (!File(fileName).exists()) {
        URL(link).openStream().use {
            Channels.newChannel(it).use { rbc ->
                FileOutputStream(fileName).use { fos ->
                    fos.channel.transferFrom(rbc, 0, Long.MAX_VALUE)
                }
            }
        }
    }

    driver.close()

    println(fileName)
    return fileName
}

@OptIn(PreviewFeature::class)
suspend fun main(args: Array<String>) {
    val bot = telegramBot(args.first())

    print(bot.getMe())

    bot.buildBehaviourWithLongPolling(CoroutineScope(currentCoroutineContext() + SupervisorJob())) {
        onCommand("idea") {
            reply(
                it,
                replyMarkup = inlineKeyboard {
                    row {
                        dataButton(WIN_64, stringFromPlatformAndIde(WIN_64, IDEA))
                        dataButton(WIN_ARM64, stringFromPlatformAndIde(WIN_ARM64, IDEA))
                    }
                    row {
                        dataButton(MAC, stringFromPlatformAndIde(MAC, IDEA))
                        dataButton(MAC_M, stringFromPlatformAndIde(MAC_M, IDEA))
                    }
                    row {
                        dataButton(LIN_86_64, stringFromPlatformAndIde(LIN_86_64, IDEA))
                        dataButton(LIN_AARCH64, stringFromPlatformAndIde(LIN_AARCH64, IDEA))
                    }
                }
            ) {
                regular("Choose platform")
            }
        }

        onMessageDataCallbackQuery {
            val (platform, ide) = it.data.parsePlatformAndIde() ?: it.let {
                answer(it, "Unsupported data")
                return@onMessageDataCallbackQuery
            }

            when (ide) {
                IDEA -> editMenu(bot, it, "Chosen platform: $platform. Choose version", inlineKeyboard {
                    row {
                        dataButton("Community", stringFromPlatformAndIde(platform, IDEA_CE))
                        dataButton("Ultimate", stringFromPlatformAndIde(platform, IDEA_UE))
                    }
                })
                IDEA_CE -> {
                    editMenu(bot, it, "Downloading $IDEA_CE for $platform", null)
                    val fileName = downloadIdea(platform, ide)
                    // I don't know why the API doesn't send the file at this point
                    // If you're reading this, it means I haven't had time to fix it
                    SendDocument(
                        it.message.chat.id,
                        File(fileName).asMultipartFile(),
                        protectContent = true
                    )
                }
                IDEA_UE -> {
                    editMenu(bot, it, "Downloading $IDEA_UE for $platform", null)
                    val fileName = downloadIdea(platform, ide)
                    // I don't know why the API doesn't send the file at this point
                    // If you're reading this, it means I haven't had time to fix it
                    SendDocument(
                        it.message.chat.id,
                        File(fileName).asMultipartFile(),
                        protectContent = true
                    )
                }
            }

            answer(it)
        }

        onUnhandledCommand {
            reply(
                it,
                "Open the menu to see commands"
            )
        }

        onChannelChatCreated {
            setChatMenuButton(it.chat.id, MenuButton.Default)
        }

        setMyCommands(BotCommand("idea", "Download latest version of IntelliJ IDEA"))

        allUpdatesFlow.subscribeSafelyWithoutExceptions(this) {
            println(it)
        }

    }.join()
}