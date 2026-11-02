// Telegram bot that sells and delivers DeepSweep Pro licence codes.
// Buyers are matched by the e-mail they left in the Boosty donation comment;
// /buy shows the product card with the price (required when connecting a payment provider).
// Deploy steps: docs/BOT.md. Secrets: TG_BOT_TOKEN, TG_SECRET, TG_ADMIN_CHAT.
import { createClient } from "jsr:@supabase/supabase-js@2";

const TG_TOKEN = Deno.env.get("TG_BOT_TOKEN") ?? "";
const TG_SECRET = Deno.env.get("TG_SECRET") ?? "";
const ADMIN = Number(Deno.env.get("TG_ADMIN_CHAT") ?? "0");
const LOW_POOL = 5;

/** Storefront shown by /buy; the price and links are changed without touching the code. */
const PRICE_RUB = Number(Deno.env.get("PRICE_RUB") ?? "99");
const BUY_URL = Deno.env.get("BUY_URL") ??
  "https://boosty.to/skeorz/posts/45a69380-9ff8-43ae-89d3-02e0eb816165";
const SUPPORT_URL = Deno.env.get("SUPPORT_URL") ?? "https://t.me/skeorz";
const PRODUCT_IMAGE = Deno.env.get("PRODUCT_IMAGE_URL") ?? "";

const db = createClient(
  Deno.env.get("SUPABASE_URL") ?? "",
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  { auth: { persistSession: false } },
);

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

const HELP = [
  "<b>DeepSweep Pro</b>",
  "",
  "• /buy — цена и покупка",
  "• пришлите e-mail из комментария к оплате — пришлю код активации",
  "• /terms — условия, /support — поддержка",
  "",
  "Код бессрочный, проверяется на устройстве, интернет для активации не нужен.",
].join("\n");

const PRODUCT = [
  "<b>DeepSweep Pro — ключ активации Premium</b>",
  "",
  "Бесконечный сапёр на дне океана. Бесплатная версия открыта всем, Pro открывает Premium:",
  "",
  "✦ весь магазин: темы, мины, буи, цифры, рамки профиля",
  "✦ опыт ×2 в любом режиме и на любой сложности",
  "✦ все заработанные кристаллы ×3",
  "✦ все режимы сразу, без прокачки уровня",
  "",
  `Стоимость: <b>${PRICE_RUB} ₽</b> — разовая покупка, без подписки.`,
  "Ключ проверяется на устройстве: интернет для активации не нужен.",
  "",
  "После оплаты пришлите боту e-mail, указанный в комментарии, — он пришлёт код.",
].join("\n");

const TERMS = [
  "<b>Условия покупки</b>",
  "",
  "• товар цифровой: ключ активации Premium для DeepSweep Pro;",
  "• оплата разовая, подписки нет;",
  "• ключ бессрочный, хранится в сохранении игры;",
  "• после выдачи ключа возврат не предусмотрен;",
  "• при переустановке игры код нужно ввести снова.",
].join("\n");

const SUPPORT = [
  "<b>Поддержка</b>",
  "",
  `Напишите: ${SUPPORT_URL} — отвечу в течение дня.`,
  "По оплате пришлите дату, сумму и e-mail из комментария к платежу.",
  "",
  "Telegram не обрабатывает платежи и не может помочь с возвратом —",
  "все вопросы по покупке решает продавец.",
].join("\n");

const HOWTO = [
  "",
  "",
  "<b>Как включить Premium</b>",
  "1. Скачайте Pro-сборку: github.com/Granit1846/DeepSweep/releases",
  "2. Магазин → ★ Premium → «У меня есть код»",
  "3. Вставьте код — Premium включится навсегда.",
].join("\n");

async function tg(method: string, body: unknown) {
  const res = await fetch(`https://api.telegram.org/bot${TG_TOKEN}/${method}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) console.error("telegram", method, res.status, await res.text());
}

const say = (chat: number, text: string) =>
  tg("sendMessage", { chat_id: chat, text, parse_mode: "HTML", disable_web_page_preview: true });

const buyKeyboard = {
  inline_keyboard: [
    [{ text: `Оплатить ${PRICE_RUB} ₽`, url: BUY_URL }],
    [{ text: "Поддержка", url: SUPPORT_URL }],
  ],
};

/** Product card: photo with caption when PRODUCT_IMAGE_URL is set, plain message otherwise. */
async function sendProduct(chat: number) {
  if (PRODUCT_IMAGE) {
    const res = await fetch(`https://api.telegram.org/bot${TG_TOKEN}/sendPhoto`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        chat_id: chat,
        photo: PRODUCT_IMAGE,
        caption: PRODUCT,
        parse_mode: "HTML",
        reply_markup: buyKeyboard,
      }),
    });
    if (res.ok) return;
    console.error("sendPhoto failed", res.status, await res.text());
  }
  await tg("sendMessage", {
    chat_id: chat,
    text: PRODUCT,
    parse_mode: "HTML",
    disable_web_page_preview: true,
    reply_markup: buyKeyboard,
  });
}

async function poolLeft(): Promise<number> {
  const { count } = await db
    .from("codes")
    .select("code", { count: "exact", head: true })
    .is("used_at", null);
  return count ?? 0;
}

/** Looks for a payment of this e-mail that has no code yet. */
async function pendingOrder(email: string): Promise<string | null> {
  const { data } = await db
    .from("payments")
    .select("order_id")
    .eq("email", email)
    .is("delivered_at", null)
    .order("paid_at", { ascending: true })
    .limit(1);
  return data?.[0]?.order_id ?? null;
}

/** Previous delivery for this e-mail, so a forgotten code can be re-sent. */
async function deliveredCode(email: string, chat: number): Promise<string | null> {
  const { data } = await db
    .from("payments")
    .select("order_id")
    .eq("email", email)
    .not("delivered_at", "is", null)
    .order("paid_at", { ascending: false })
    .limit(1);
  const order = data?.[0]?.order_id;
  if (!order) return null;
  const { data: row } = await db
    .from("codes")
    .select("code, used_by")
    .eq("order_id", order)
    .maybeSingle();
  return row && Number(row.used_by) === chat ? row.code : null;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("ok");
  if (!TG_SECRET || req.headers.get("x-telegram-bot-api-secret-token") !== TG_SECRET) {
    return new Response("forbidden", { status: 403 });
  }

  const update = await req.json().catch(() => null);
  const chat: number | undefined = update?.message?.chat?.id;
  const text: string = (update?.message?.text ?? "").trim();
  if (!chat || !text) return new Response("ok");
  // "/buy@deepsweep_pro_bot" must work too
  const cmd = text.split(/\s+/)[0].split("@")[0].toLowerCase();

  if (cmd === "/start" || cmd === "/help" || cmd === "/menu") {
    await say(chat, HELP);
    return new Response("ok");
  }
  if (cmd === "/buy" || cmd === "/price" || cmd === "/shop") {
    await sendProduct(chat);
    return new Response("ok");
  }
  if (cmd === "/terms") {
    await say(chat, TERMS);
    return new Response("ok");
  }
  if (cmd === "/support" || cmd === "/paysupport") {
    await say(chat, SUPPORT);
    return new Response("ok");
  }

  // --- admin commands, only from TG_ADMIN_CHAT ---
  if (ADMIN && chat === ADMIN) {
    if (text.startsWith("/add")) {
      const [, order, email, amount] = text.split(/\s+/);
      if (!order || !email || !amount || !EMAIL.test(email.toLowerCase())) {
        await say(chat, "Формат: /add &lt;номер заказа&gt; &lt;email&gt; &lt;сумма&gt;");
        return new Response("ok");
      }
      const { error } = await db.from("payments").insert({
        order_id: order,
        email: email.toLowerCase(),
        amount: Number(amount),
      });
      await say(chat, error ? `Не добавил: ${error.message}` : `Добавил ${order} / ${email} / ${amount} ₽`);
      return new Response("ok");
    }
    if (text === "/pool") {
      await say(chat, `В пуле осталось кодов: ${await poolLeft()}`);
      return new Response("ok");
    }
  }

  const email = text.toLowerCase();
  if (!EMAIL.test(email)) {
    await say(chat, "Это не похоже на e-mail.\nПришлите адрес, который указали в комментарии к оплате.");
    return new Response("ok");
  }

  const order = await pendingOrder(email);
  if (order) {
    const { data: code, error } = await db.rpc("claim_code", { p_order: order, p_chat: chat });
    if (error) {
      const empty = String(error.message).includes("pool_empty");
      await say(
        chat,
        empty
          ? "Коды на складе закончились — напишите продавцу, выдадим вручную в течение дня."
          : "Не получилось выдать код, напишите продавцу.",
      );
      if (ADMIN) await say(ADMIN, `Ошибка выдачи ${order}: ${error.message}`);
      return new Response("ok");
    }
    await say(chat, `Ваш код активации:\n\n<code>${code}</code>${HOWTO}`);
    const left = await poolLeft();
    if (ADMIN && left <= LOW_POOL) await say(ADMIN, `В пуле осталось ${left} кодов — пора догрузить.`);
    return new Response("ok");
  }

  const again = await deliveredCode(email, chat);
  if (again) {
    await say(chat, `Ваш код (повторно):\n\n<code>${again}</code>${HOWTO}`);
    return new Response("ok");
  }

  await say(
    chat,
    "Платёж с таким e-mail не найден.\n\n" +
      "Проверьте, что это тот адрес, который вы указали в комментарии к донату. " +
      "Если оплата только что прошла — попробуйте через 10 минут.",
  );
  return new Response("ok");
});
