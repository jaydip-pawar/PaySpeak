package com.pp.payspeak.core.parser

// ================================================================
//  SMS AMOUNT EXTRACTION ENGINE  v5
//
//  Categories handled:
//    GENUINE_TRANSACTION  — real money moved (UPI / NEFT / IMPS etc.)
//    BILL_STATEMENT       — credit card / loan statement, no money moved yet
//    SPAM_PROMOTIONAL     — ads, gaming, investment scams, offers
//    PHISHING_LOOKALIKE   — fake bank/wallet SMS with suspicious domains
//    INFORMATIONAL        — OTP, balance alert, service message
//    UNCERTAIN
// ================================================================

enum class TxnType { CREDIT, DEBIT, PAYMENT_DUE, UNKNOWN }

enum class SmsCategory {
    GENUINE_TRANSACTION,
    BILL_STATEMENT,
    SPAM_PROMOTIONAL,
    PHISHING_LOOKALIKE,
    INFORMATIONAL,
    UNCERTAIN
}

// ── Data classes ──────────────────────────────────────────────────

data class AmountCandidate(
    val raw: String,
    val value: Double,
    val tokenIndex: Int,
    val hasCurrencyPrefix: Boolean,
    val matchType: String,
    val score: Double = 0.0,
    val reasons: List<String> = emptyList()
)

data class TxnClassification(
    val type: TxnType,
    val creditScore: Double,
    val debitScore: Double,
    val reasons: List<String>
)

data class SpamClassification(
    val category: SmsCategory,
    val spamScore: Double,
    val genuineScore: Double,
    val confidence: String,
    val reasons: List<String>
)

data class SmsExtractionResult(
    val sms: String,
    val spamResult: SpamClassification,
    val txnType: TxnClassification,
    val winner: AmountCandidate?,
    val secondaryAmount: AmountCandidate?,
    val dueDate: String?,
    val allCandidates: List<AmountCandidate>
)

// ================================================================
//  ENGINE
// ================================================================

object SmsAmountEngine {

    private val CURRENCY_PREFIX_RE = Regex(
        """(?:rs\.?\s*|inr\s*|₹\s*)(?:(?:dr|cr)\.?\s*)?([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    private val DECIMAL_RE  = Regex("""(?<![.\d])([\d,]{1,12}\.\d{1,2})(?![.\d])""")
    private val INTEGER_RE  = Regex("""(?<![.\d])([1-9][\d,]{0,11})(?![.\d])""")
    private val LAKH_RE     = Regex("""([\d.]+)\s*lakh""",  RegexOption.IGNORE_CASE)
    private val CRORE_RE    = Regex("""([\d.]+)\s*crore""", RegexOption.IGNORE_CASE)
    private val SPLIT_RE    = Regex("""[\s\t\r\n:;|()\[\]{}<>!@#^&*+='"?/\\]+""")
    private val DUE_DATE_RE = Regex(
        """(?:due\s+(?:on|by|date)\s*:?\s*)([\d]{1,2}[-/.][\d]{2}[-/.][\d]{2,4})""",
        RegexOption.IGNORE_CASE
    )

    private val POS_STRONG = setOf(
        "credited","debited","paid","received","transferred","deposited",
        "withdrawn","sent","charged","deducted","spent","remitted","settled",
        "cleared","processed","purchase","purchased","bought","loaded","topped",
        "refunded","reversed","returned","reimbursed","repaid",
        "auto-debited","auto-credited","autodebit","autocredit",
        "mandate executed","si executed","emi paid","emi debited",
        "bill paid","bill payment","autopay","auto pay",
        "standing instruction","fund transfer","amount debited",
        "amount credited","amount paid","txn successful",
        "transaction successful","payment successful",
        "payment done","payment made","payment processed",
        "checkout","withdrawal","cash withdrawal","cash deposit"
    )

    private val POS_WEAK = setOf(
        "rs","inr","amount","amt","upi","neft","imps","rtgs","nach","ecs",
        "aeps","bbps","txn","transaction","transfer","payment","pay",
        "credit","debit","via","through","towards","wallet","bank",
        "swift","wire","online","digital",
        "paytm","phonepe","gpay","googlepay","bhim","amazonpay",
        "mobikwik","freecharge","lazypay","cred","slice"
    )

    private val NEG_STRONG = setOf(
        "ref","reference","refno","refid","txnid","transactionid",
        "id","no","num","number","sr","serial","seq",
        "order","invoice","tracking","ticket","case","complaint",
        "application","policy","member","customer","client",
        "otp","code","pin","mpin","ipin","tpin","password","cvv",
        "account","acct","ac","ending","xxxx","last","tail",
        "utr","arn","rrn","auth","approval","merchant","terminal",
        "pos","atm","mobile","phone","contact","tel","cell"
    )

    private val NEG_WEAK = setOf(
        "balance","bal","avl","available","avlbal","avbl",
        "closing","opening","ledger","remaining","outstanding","aggregate",
        "principal","interest","penalty","charge","fee","charges",
        "gst","tax","tds","cess","surcharge","maximum","max",
        "limit","credit limit","card limit","sanctioned",
        "date","time","on","at","as","of"
    )

    private val AMOUNT_LABEL_BOOST = setOf(
        "total","total amt","total amount","amt","amount",
        "txn amount","transaction amount","bill amount",
        "payable amount","net amount","gross amount",
        "net payable","total payable","total due","amount due",
        "total outstanding","outstanding amount"
    )

    private val MIN_DUE_MARKERS = setOf(
        "min","minimum","min due","minimum due",
        "min amt","min amt due","minimum amount due",
        "min payable","minimum payable"
    )

    private val CREDIT_STRONG = setOf(
        "credited","received","deposited","added","refunded","cashback",
        "reversed","loaded","incoming","inward","receipt","proceeds",
        "matured","maturity","dividend","salary","wages","pension",
        "subsidy","reimbursement","reimburse","repaid",
        "fund received","money received","amount received",
        "payment received","credited back","bounced back",
        "auto-credit","autocredit","topped up","topup",
        "salary credited","wages credited","pension credited",
        "refund credited","cashback credited","reward credited",
        "interest credited","dividend credited","fd matured",
        "rd matured","maturity credited","proceeds credited",
        "insurance claim credited","settlement credited",
        "neft received","imps received","upi received",
        "rtgs received","ecs credit","nach credit",
        "cr.","cr amount","amount cr","inr cr","rs cr"
    )

    private val CREDIT_WEAK = setOf(
        "from","into","inward","by","via","through",
        "into your","to your account","in your account",
        "towards your account","your account has been"
    )

    private val DEBIT_STRONG = setOf(
        "debited","paid","payment","withdrawn","withdrawal",
        "deducted","charged","spent","sent","transferred",
        "outward","purchase","purchased","bought","checkout",
        "auto-debit","autodebit","mandate","emi","instalment",
        "standing instruction","si","sweep out","auto pay","autopay",
        "bill payment","utility payment","insurance premium",
        "subscription","renewal",
        "emi debited","loan emi","card emi","emi paid",
        "mandate executed","nach debit","ecs debit",
        "si executed","auto-debited","scheduled debit",
        "bill paid","electricity bill paid","gas bill paid",
        "water bill paid","recharge done","dth recharged",
        "order placed","order confirmed","booking confirmed",
        "ticket booked","hotel booked","flight booked",
        "txn done","transaction done","payment done",
        "amount deducted","amount debited","fund transferred",
        "neft sent","imps sent","upi sent","rtgs sent",
        "cash withdrawn","atm withdrawal","pos transaction",
        "card transaction","swipe transaction","tap to pay",
        "dr.","dr amount","amount dr","inr dr","rs dr",
        "total amt","total amount due","amount due","amt due",
        "min amt due","minimum amount due","minimum due",
        "outstanding amount","outstanding balance",
        "bill generated","statement generated",
        "credit card bill","card bill","card statement"
    )

    private val DEBIT_WEAK = setOf(
        "to","towards","for","against","on behalf",
        "payable to","sent to","transferred to","paid to",
        "charged to","debited from your"
    )

    private val BILL_STATEMENT_HARD = setOf(
        "statement generated","statement for","bill generated",
        "credit card statement","card statement","cc statement",
        "your statement","monthly statement","account statement",
        "total amt due","total amount due","total outstanding",
        "minimum amount due","minimum amt due","min amt due",
        "min due","due on","due date","due by","pay by","pay before",
        "payment due","payment due date","due amount",
        "ccpaynow","cc pay","card payment link","pay your bill",
        "view statement","download statement","e-statement",
        "billing cycle","billing period","statement period",
        "unbilled amount","unbilled","overdue","overlimit",
        "loan statement","loan account statement",
        "emi schedule","repayment schedule","amortization"
    )

    private val SPAM_HARD = setOf(
        "rummy","rummycircle","rummy circle","junglee rummy",
        "ace2three","a23","adda52","pokerbaazi","poker baazi",
        "poker","casino","slot machine","blackjack","baccarat",
        "bet","betting","gamble","gambling","wager","wagering",
        "fantasy","dream11","dream 11","my11circle","my11 circle",
        "11wickets","ballebaazi","halaplay","starpick","gamezy",
        "fantain","playerzpot","myteam11","sports11","howzat",
        "winzo","wintzo","zupee","qureka","gameskraft",
        "mpl","mobile premier league","jeetwin","bet365",
        "1xbet","parimatch","betway","melbet","lottoland",
        "lottery","lucky draw","bumper draw","lucky number",
        "satta","matka","tambola","housie","bingo","keno",
        "spin the wheel","spin to win","wheel of fortune",
        "jackpot","mega jackpot","daily jackpot","super jackpot",
        "prize pool","prize money","prize winning","prize draw",
        "scratch card","scratch and win","scratch to reveal",
        "teen patti","teenpatti","flash","andar bahar",
        "ludo earn","chess earn","carrom earn",
        "skill game","skill gaming","real cash game",
        "win real cash","win real money","earn real money",
        "winning streak","big winner","top winner",
        "install now","install app","install the app",
        "download now","download app","download the app",
        "get app","get the app","open app","use app",
        "app link","apk download",
        "click here","click now","tap here","tap now",
        "swipe up","scan qr","scan code","scan to download",
        "open link","follow link","visit link","check link",
        "link below","link in bio","link is valid",
        "register now","register today","register free",
        "sign up","signup","create account now",
        "join now","join today","join free",
        "enroll now","enroll today","activate now",
        "click to activate","click & activate","click and activate",
        "unlock your reward","unlock reward","unlock offer",
        "unlock cashback","unlock bonus",
        "loan approved","loan sanctioned","loan eligible",
        "pre-approved","pre approved","preapproved",
        "pre-qualified","pre qualified",
        "instant loan","quick loan","fast loan","same day loan",
        "personal loan offer","personal loan approved",
        "business loan offer","home loan offer",
        "gold loan offer","vehicle loan offer","car loan offer",
        "education loan offer","mudra loan offer",
        "credit line approved","overdraft approved",
        "instant approval","instant disbursal","same day disbursal",
        "no documents","zero documents","paperless loan",
        "minimal documents","no income proof","no collateral",
        "apply for loan","apply loan","get loan now",
        "loan offer","exclusive loan",
        "emi starts at","zero emi","no cost emi offer",
        "guaranteed return","guaranteed returns","guaranteed profit",
        "assured return","assured returns","assured profit",
        "fixed return","high return","100% return",
        "double your money","triple your money","10x return",
        "daily profit","weekly profit","monthly profit",
        "passive income","earn daily","earn weekly","earn monthly",
        "earn from home","work from home earn","earn sitting home",
        "paid survey","survey earn","earn by survey",
        "refer and earn unlimited","referral income",
        "mlm","multi level marketing","network marketing",
        "chain marketing","pyramid scheme","ponzi",
        "crypto profit","bitcoin profit","ethereum profit",
        "forex signal","forex profit","forex trading offer",
        "option trading tips","intraday tips","stock tips",
        "free tips","multibagger tips","penny stock tips",
        "trading signal","trading tips","market tips",
        "algo trading","auto trading profit","bot trading",
        "invest now","golden opportunity",
        "money making opportunity","business opportunity",
        "exclusive offer","limited offer","special offer",
        "offer expires","offer valid","offer ends today",
        "offer ends tonight","offer ends soon","offer ending",
        "claim now","claim your","claim reward","claim prize",
        "claim cashback","claim bonus","claim gift",
        "redeem now","redeem your","redeem points","redeem reward",
        "use code","promo code","coupon code","discount code",
        "voucher code","gift voucher","gift card","e-voucher",
        "flat discount","flat off","extra discount","extra off",
        "upto 100% off","upto 90% off","upto 80% off",
        "cashback offer","wallet offer","bank offer","card offer",
        "buy one get one","bogo","buy 2 get 1","combo offer",
        "free delivery","free shipping","zero delivery charge",
        "first order free","first ride free","first booking free",
        "refer friend get","invite friend earn","share and earn",
        "no cost emi","zero cost emi","bnpl offer",
        "buy now pay later",
        "congratulations","congrats","dear winner",
        "you have won","you won","you are selected",
        "you are chosen","you are eligible","you qualify",
        "selected for","chosen for","eligible for","qualify for",
        "verified user reward","trusted user reward",
        "lucky user","lucky customer","lucky winner",
        "booking open","seats limited","stock limited",
        "limited seats","limited time","limited period",
        "valid today only","valid today","expires tonight",
        "midnight offer","flash sale","mega sale","big sale",
        "end of season","clearance sale","festive sale",
        "diwali offer","holi offer","eid offer","rakhi offer",
        "christmas offer","new year offer","republic day offer",
        "independence day offer","navratri offer","pongal offer",
        "hurry","hurry up","last chance","last opportunity",
        "dont miss","don't miss","do not miss","never miss",
        "act now","act fast","respond now","respond today",
        "apply now","book now","order now","buy now",
        "best offer","best deal","best price","lowest price",
        "never before offer","first time ever","biggest ever",
        "unbelievable offer","incredible deal","amazing offer",
        "mind blowing offer","explosive deal","whopping discount",
        "free bonus","welcome bonus","joining bonus","signup bonus",
        "referral bonus","loyalty bonus","daily bonus",
        "free cash","free money","earn free","get free",
        "no investment","zero investment","free to join",
        "bit.ly","tinyurl","shorturl","goo.gl","ow.ly",
        "t.co","fb.me","buff.ly","ift.tt","wp.me",
        "is.gd","tiny.cc","cutt.ly","shorturl.at","rb.gy",
        "clck.ru","snip.ly","mcaf.ee","tiny.one","vo.la"
    )

    private val SPAM_SOFT = setOf(
        "free","bonus","gift","prize","reward","winning","lucky",
        "wow","amazing","fabulous","fantastic","awesome",
        "unbelievable","incredible","mind-blowing","explosive",
        "massive","huge","gigantic","enormous","whopping",
        "bumper","mega","super","grand","big","hurry","urgent",
        "immediately","asap","today only","midnight","tonight",
        "instant","quick","exclusive","vip","premium","gold",
        "platinum","diamond","elite","special","unique","rare",
        "one-time","select","chosen","selected","privileged",
        "early access","try","explore","discover","unlock",
        "activate","boost","upgrade","enhance","maximize",
        "celebrate","enjoy","relax","surprise","mystery",
        "hidden","secret","reveal","scratch","open","unbox",
        "play","spin","slot","wheel","chips","coins","tokens",
        "tournament","table","game","level","score","rank",
        "leaderboard","challenge","quest","mission","streak"
    )

    private val GENUINE_HARD = setOf(
        "a/c","acct","account no","account number",
        "your account","your a/c","ac no",
        "upi ref","upi ref no","upi ref id","upi ref number",
        "ref no","ref id","refno","txn id","txn no","txn ref",
        "transaction id","transaction no","transaction ref",
        "transaction number","utr no","utr","rrn","arn",
        "approval code","auth code","authorization code",
        "neft","imps","rtgs","nach","ecs","upi","aeps","bbps",
        "ifsc","micr","swift","iban","sort code",
        "available balance","avl bal","avl balance",
        "closing balance","opening balance","ledger balance",
        "current balance","account balance",
        "otp","one time password","do not share",
        "do not disclose","never share","keep confidential",
        "not asked for","bank never asks",
        "mandate ref","nach mandate","si ref","ecs ref",
        "autopay ref","standing instruction ref",
        "card ending","card no","debit card","credit card",
        "card transaction","pos transaction","tap to pay",
        "contactless","chip transaction","swipe",
        "atm withdrawal","atm transaction","cdm deposit",
        "cash deposit machine","cash withdrawal",
        "net banking","mobile banking","internet banking",
        "upi id","vpa","virtual payment address",
        "upi pin","mpin","ipin","tpin",
        "emi","equated monthly","loan account","loan a/c",
        "loan ref","loan id","mandate executed",
        "call 1800","call 18001","helpline","customer care",
        "dispute","if not done by you","not done by you",
        "report immediately","block card","block account"
    )

    private val GENUINE_SOFT = setOf(
        "dear customer","dear user","dear sir","dear madam",
        "dear valued","respected customer","esteemed customer",
        "dear account holder","dear card holder",
        "dear member","dear client","dear subscriber",
        "state bank of india","state bank","sbi","sbi bank",
        "punjab national bank","pnb",
        "bank of baroda","bob","baroda bank",
        "bank of india","boi",
        "canara bank","canara",
        "union bank of india","union bank",
        "central bank of india","central bank",
        "indian bank",
        "indian overseas bank","iob",
        "uco bank","uco",
        "bank of maharashtra","mahabank",
        "united bank of india","united bank",
        "allahabad bank",
        "andhra bank",
        "vijaya bank",
        "dena bank",
        "corporation bank",
        "syndicate bank",
        "oriental bank of commerce","obc","oriental bank",
        "punjab and sind bank","psb bank",
        "hdfc bank","hdfc",
        "icici bank","icici",
        "axis bank","axis",
        "kotak mahindra bank","kotak mahindra","kotak bank","kotak",
        "yes bank","yes financial",
        "indusind bank","indusind",
        "rbl bank","rbl",
        "dcb bank","dcb",
        "federal bank","federal",
        "karur vysya bank","kvb",
        "city union bank","cub",
        "south indian bank","sib",
        "catholic syrian bank","csb bank","csb",
        "dhanlaxmi bank","dhanlaxmi",
        "lakshmi vilas bank","lvb",
        "nainital bank",
        "tamilnad mercantile bank","tmb",
        "jammu and kashmir bank","j&k bank","jk bank",
        "jammu kashmir bank",
        "karnataka bank",
        "bandhan bank","bandhan",
        "au small finance bank","au bank","au sfb",
        "equitas small finance bank","equitas bank","equitas sfb","equitas",
        "suryoday small finance bank","suryoday bank","suryoday",
        "ujjivan small finance bank","ujjivan bank","ujjivan sfb","ujjivan",
        "esaf small finance bank","esaf bank","esaf",
        "fincare small finance bank","fincare bank","fincare",
        "jana small finance bank","jana bank","jana sfb",
        "utkarsh small finance bank","utkarsh bank","utkarsh sfb",
        "idbi bank","idbi",
        "idfc first bank","idfc first","idfc bank","idfc",
        "saraswat bank","saraswat cooperative",
        "abhyudaya cooperative bank","abhyudaya bank","abhyudaya",
        "cosmos cooperative bank","cosmos bank","cosmos",
        "shamrao vithal cooperative bank","svc bank","svcb",
        "hsbc bank","hsbc",
        "standard chartered bank","standard chartered","stanchart",
        "citibank india","citibank","citi bank","citi",
        "deutsche bank india","deutsche bank","deutsche",
        "dbs bank india","dbs bank","dbs",
        "american express bank","american express","amex",
        "phonepe","phone pe","phonepay",
        "google pay","googlepay","gpay","tez",
        "paytm","paytm bank","paytm payments bank",
        "bhim","bhim upi","bhim app",
        "amazon pay","amazonpay",
        "airtel payments bank","airtel money","airtel bank",
        "jio payments bank","jio money","jio bank","jiomoney",
        "mobikwik","mobi kwik",
        "freecharge","free charge",
        "lazypay","lazy pay",
        "slice card","slice",
        "jupiter money","jupiter bank","jupiter",
        "fi money","federal bank fi",
        "niyo global","niyo solutions","niyo",
        "pockets by icici","icici pockets","pockets",
        "yono sbi","yono cash","yono business","yono",
        "sbi pay","sbi upi",
        "hdfc payzapp","payzapp",
        "icici imobile","imobile pay","imobile",
        "axis pay","axis mobile",
        "npci","national payments corporation",
        "juspay","razorpay","cashfree","instamojo",
        "mastercard","visa card","visa","rupay","maestro",
        "jio","reliance jio","airtel","vi","vodafone idea","vodafone",
        "bsnl","mtnl",
        "irctc","redbus",
        "ola cabs","ola","uber","rapido",
        "nabard","sidbi","mudra bank","mudra",
        "lic","life insurance corporation","lic premium",
        "hdfc life","icici prudential","sbi life","bajaj allianz life",
        "zerodha","groww","upstox","angel broking","angel one",
        "nse","bse","nsdl","cdsl",
        "sip","systematic investment","mutual fund","mf","nav",
        "amazon","flipkart","myntra","swiggy","zomato",
        "makemytrip","goibibo","bookmyshow"
    )

    private val OFFICIAL_DOMAINS = setOf(
        "onlinesbi.com","sbi.co.in","sbionline.com",
        "pnbindia.in","netpnb.com",
        "bankofbaroda.in","bobibanking.com",
        "bankofindia.co.in",
        "canarabank.com","canarabanking.com",
        "unionbankofindia.co.in","unionbankonline.co.in",
        "centralbankofindia.co.in",
        "indianbank.net.in","indianbank.in",
        "iobnet.co.in","iob.in",
        "ucobank.com","ucobank.co.in",
        "bankofmaharashtra.in",
        "hdfcbank.com","hdfc.com",
        "icicibank.com","icicidirect.com",
        "axisbank.com","axis.bank.in","ccm.axis.bank.in",
        "kotak.com","kotakbank.com","kotak811.com",
        "yesbank.in","yesonline.in",
        "indusind.com","indusonlinepayment.com",
        "rblbank.com",
        "dcbbank.com",
        "federalbank.co.in","fednet.in",
        "aubank.in","aubankltd.com",
        "equitasbank.com",
        "ujjivansfb.in",
        "bandhanbank.com",
        "idfcfirstbank.com",
        "paytm.com","paytmbank.com",
        "phonepe.com","phonepecorp.com",
        "gpay.app","pay.google.com",
        "amazon.in","amazonpay.in",
        "mobikwik.com",
        "freecharge.com","freecharge.in",
        "airtel.in","airtelbank.com",
        "jio.com","jiomoney.com",
        "npci.org.in","bhimupi.org.in","upi.one",
        "razorpay.com","cashfree.com","instamojo.com",
        "ccavenue.com","billdesk.com","payu.in","payumoney.com",
        "rbi.org.in","epfindia.gov.in","incometax.gov.in",
        "gst.gov.in","uidai.gov.in","mca.gov.in"
    )

    private val BRAND_NAMES_FOR_PHISHING = setOf(
        "paytm","phonepe","gpay","google","amazon","flipkart",
        "sbi","hdfc","icici","axis","kotak","pnb","bob","canara",
        "yesbank","indusind","rbl","federal","bandhan","idfc",
        "airtel","jio","bsnl","ola","uber","swiggy","zomato",
        "irctc","lic","npci","upi","bhim","mobikwik","freecharge",
        "razorpay","cashfree","payu","billdesk"
    )

    // ── Preprocessing ────────────────────────────────────────────────

    private fun normalize(sms: String): String {
        var s = sms
        val devMap = mapOf(
            '०' to '0','१' to '1','२' to '2','३' to '3','४' to '4',
            '५' to '5','६' to '6','७' to '7','८' to '8','९' to '9'
        )
        s = s.map { devMap.getOrDefault(it, it) }.joinToString("")
        s = LAKH_RE.replace(s)  { mr -> (mr.groupValues[1].toDoubleOrNull()?.times(100000)?.toLong()  ?: mr.value).toString() }
        s = CRORE_RE.replace(s) { mr -> (mr.groupValues[1].toDoubleOrNull()?.times(10000000)?.toLong() ?: mr.value).toString() }
        s = s.replace(Regex("""(\d)-(\d)"""), "$1$2")
        s = s.lowercase()
        return s
    }

    private fun tokenize(n: String): List<String> =
        n.split(SPLIT_RE).map { it.trim(',') }.filter { it.isNotBlank() }

    private fun parseAmount(s: String) = s.replace(",", "").toDoubleOrNull()

    private fun extractDueDate(sms: String): String? =
        DUE_DATE_RE.find(sms)?.groupValues?.get(1)

    // ── Candidate Extraction ─────────────────────────────────────────

    private fun extractCandidates(norm: String, tokens: List<String>): List<AmountCandidate> {
        val seen = mutableSetOf<Double>()
        val out  = mutableListOf<AmountCandidate>()
        fun idxFor(start: Int): Int {
            var chars = 0
            for ((i, t) in tokens.withIndex()) { chars += t.length + 1; if (chars > start) return i }
            return tokens.lastIndex
        }
        CURRENCY_PREFIX_RE.findAll(norm).forEach { mr ->
            val v = parseAmount(mr.groupValues[1]) ?: return@forEach
            if (v < 0.01 || v in seen) return@forEach; seen += v
            out += AmountCandidate(mr.groupValues[1], v, idxFor(mr.range.first), true, "currency_prefix")
        }
        DECIMAL_RE.findAll(norm).forEach { mr ->
            val v = parseAmount(mr.groupValues[1]) ?: return@forEach
            if (v < 0.01 || v in seen) return@forEach; seen += v
            out += AmountCandidate(mr.groupValues[1], v, idxFor(mr.range.first), false, "decimal")
        }
        INTEGER_RE.findAll(norm).forEach { mr ->
            val v = parseAmount(mr.groupValues[1]) ?: return@forEach
            if (v < 1.0 || v in seen) return@forEach; seen += v
            out += AmountCandidate(mr.groupValues[1], v, idxFor(mr.range.first), false, "integer")
        }
        return out
    }

    // ── Amount Context Scoring ───────────────────────────────────────

    private fun scoreCandidate(c: AmountCandidate, tokens: List<String>): AmountCandidate {
        val r = mutableListOf<String>()
        var s = 0.0
        if (c.hasCurrencyPrefix) { s += 25.0; r += "+25  currency prefix (Rs/INR/₹ [Dr/Cr])" }

        val lo = maxOf(0, c.tokenIndex - 4)
        val hi = minOf(tokens.lastIndex, c.tokenIndex + 4)
        for (i in lo..hi) {
            val tok  = tokens[i].trimEnd('.', ',', ':', ';')
            val dist = kotlin.math.abs(i - c.tokenIndex).toDouble()
            val d    = 1.0 / (1.0 + dist)
            when (tok) {
                in POS_STRONG         -> { s += 12.0*d;  r += "+${"%.1f".format(12.0*d)} '$tok' (strong pos)" }
                in POS_WEAK           -> { s +=  6.0*d;  r += "+${"%.1f".format(6.0*d)} '$tok' (weak pos)" }
                in AMOUNT_LABEL_BOOST -> { s += 10.0*d;  r += "+${"%.1f".format(10.0*d)} '$tok' (amount label)" }
                in NEG_STRONG         -> { s -= 18.0*d;  r += "-${"%.1f".format(18.0*d)} '$tok' (strong neg)" }
                in NEG_WEAK           -> { s -=  8.0*d;  r += "-${"%.1f".format(8.0*d)} '$tok' (weak neg)" }
            }
        }

        val nearby2 = (maxOf(0, c.tokenIndex-2)..minOf(tokens.lastIndex, c.tokenIndex+2))
            .map { tokens[it].trimEnd('.', ',', ':', ';') }
        if (nearby2.any { it in MIN_DUE_MARKERS }) {
            s -= 18.0; r += "-18  minimum-due marker nearby"
        }

        val digs = c.raw.replace(",", "").replace(".", "")
        when {
            digs.length >= 12 -> { s -= 25.0; r += "-25  12+ digits (reference/txn id)" }
            digs.length >= 9  -> { s -= 15.0; r += "-15  9-11 digits (likely ref no)" }
            digs.length >= 7  -> { s -=  5.0; r += "-5   7-8 digits (possible ref)" }
        }
        when {
            c.value in 1.0..500_000.0 -> { s +=  5.0; r += "+5   normal txn range (1–5L)" }
            c.value > 999_999.0       -> { s -= 20.0; r += "-20  value too large (>10L)" }
        }
        if (c.value < 10.0 && !c.hasCurrencyPrefix) { s -= 10.0; r += "-10  tiny value, no prefix" }
        if (c.raw.contains("."))                     { s +=  4.0; r += "+4   decimal format" }
        if (c.value >= 50.0 && c.value % 50.0 == 0.0) { s += 2.0; r += "+2   round multiple of 50" }

        return c.copy(score = s, reasons = r)
    }

    // ── Hard Disqualification ────────────────────────────────────────

    private fun disqualifyReason(c: AmountCandidate, tokens: List<String>): String? {
        val raw = c.raw.replace(",", "").replace(".", "")
        if (raw.length == 10 && raw[0] in '6'..'9') return "10-digit Indian mobile number"
        if (raw.length >= 12 && !c.hasCurrencyPrefix) return "12+ digit reference/txn number"
        val left = if (c.tokenIndex > 0) tokens[c.tokenIndex - 1].trimEnd('.', ',', ':', ';') else ""
        val hardNeg = setOf(
            "ref","refno","refid","id","no","otp","code","pin","mpin",
            "ending","xxxx","ac","acct","account","utr","rrn","arn",
            "auth","approval","serial","sr","order","invoice","tracking",
            "ticket","case","policy","member","customer","upi"
        )
        if (left in hardNeg && raw.length >= 9) return "long digits right after '$left'"
        val balTrig = setOf("balance","bal","avl","avlbal","available","avbl","limit","outstanding","due")
        if (left in balTrig && !c.hasCurrencyPrefix) return "balance/limit field"
        return null
    }

    // ── URL Analysis ─────────────────────────────────────────────────

    private data class UrlAnalysis(
        val hasUrl: Boolean,
        val isOfficial: Boolean,
        val isLookalike: Boolean,
        val lookalikeBrand: String?
    )

    private fun analyzeUrls(sms: String): UrlAnalysis {
        val lower = sms.lowercase()
        val hasUrl = Regex("""https?://|www\.|\.[a-z]{2,4}/""").containsMatchIn(lower)
        if (!hasUrl) return UrlAnalysis(false, false, false, null)

        val isOfficial = OFFICIAL_DOMAINS.any { lower.contains(it) }

        var lookalikeBrand: String? = null
        for (brand in BRAND_NAMES_FOR_PHISHING) {
            val lookalikePat = Regex("""${Regex.escape(brand)}[-.](?!com\b|co\.in\b|org\.in\b|net\.in\b)(\w+)\.\w{2,4}""")
            if (lookalikePat.containsMatchIn(lower)) {
                val found = lookalikePat.find(lower)?.value ?: ""
                if (!OFFICIAL_DOMAINS.any { lower.contains(it) && found.contains(it) }) {
                    lookalikeBrand = brand
                    break
                }
            }
        }
        return UrlAnalysis(hasUrl, isOfficial, lookalikeBrand != null, lookalikeBrand)
    }

    // ── Bill/Statement Detector ──────────────────────────────────────

    private fun isBillStatement(lower: String): Pair<Boolean, Double> {
        var score = 0.0
        for (kw in BILL_STATEMENT_HARD) {
            if (lower.contains(kw)) score += if (kw.length > 10) 20.0 else 12.0
        }
        val hasTotal = lower.contains("total amt") || lower.contains("total amount")
        val hasMinDue = lower.contains("min amt") || lower.contains("minimum") || lower.contains("min due")
        if (hasTotal && hasMinDue) score += 35.0
        if (Regex("""inr\s+dr\.?\s*[\d,]+""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) score += 15.0
        return (score >= 30.0) to score
    }

    // ── Spam Classifier ──────────────────────────────────────────────

    fun classifySpam(sms: String): SpamClassification {
        val lower   = sms.lowercase()
        val tokens  = lower.split(SPLIT_RE).map { it.trim(',') }.filter { it.isNotBlank() }
        val r       = mutableListOf<String>()
        var spam    = 0.0
        var genuine = 0.0

        val (isBill, billScore) = isBillStatement(lower)
        if (isBill) {
            genuine += billScore.coerceAtMost(60.0)
            r += "[GENUINE+${billScore.coerceAtMost(60.0).toInt()}] bill/statement signals"
            return SpamClassification(SmsCategory.BILL_STATEMENT, 0.0, minOf(genuine, 100.0), "HIGH", r)
        }

        for (kw in SPAM_HARD) {
            if (lower.contains(kw)) {
                val pts = when {
                    kw.length >= 15 -> 28.0
                    kw.length >= 8  -> 18.0
                    else            -> 10.0
                }
                spam += pts; r += "[SPAM   +${pts.toInt()}] '$kw'"
            }
        }
        for (tok in tokens) {
            if (tok in SPAM_SOFT) { spam += 4.0; r += "[SPAM    +4] soft '$tok'" }
        }

        for (kw in GENUINE_HARD) {
            if (lower.contains(kw)) {
                val pts = if (kw.length >= 8) 20.0 else 10.0
                genuine += pts; r += "[GENUINE+${pts.toInt()}] '$kw'"
            }
        }
        for (kw in GENUINE_SOFT) {
            val matched = if (kw.length <= 5) {
                Regex("""\b${Regex.escape(kw)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
            } else {
                lower.contains(kw)
            }
            if (matched) { genuine += 6.0; r += "[GENUINE +6] '$kw'" }
        }

        val hasAcct = Regex("""(a/c|acct|account|xx\d+|\bx{2,}\d+|card\s*ending|\d{4})""").containsMatchIn(lower)
        if (hasAcct) { genuine += 12.0; r += "[GENUINE+12] account/card pattern" }

        val hasDateTime = Regex("""\d{1,2}[.\-/]\d{2}[.\-/]\d{2,4}|\d{2}:\d{2}(:\d{2})?""").containsMatchIn(lower)
        if (hasDateTime) { genuine += 10.0; r += "[GENUINE+10] date/time stamp" }

        val hasRefNum = Regex("""(?:ref|txn|upi|utr|rrn|id|no)\D{0,6}(\d{9,16})""", RegexOption.IGNORE_CASE)
            .containsMatchIn(lower)
        if (hasRefNum) { genuine += 18.0; r += "[GENUINE+18] transaction reference number" }

        val hasCreditWord = lower.contains("credited") || lower.contains("credit")
        val hasCTA = Regex("""(click|tap|visit|open|download|install|activate|unlock|register|sign.?up|join)\s*(here|now|today|link|app|to)""",
            RegexOption.IGNORE_CASE).containsMatchIn(lower)
        if (hasCreditWord && hasCTA && !hasRefNum) {
            spam += 35.0
            r += "[SPAM   +35] credit-lookalike: 'credited/credit' + CTA but NO transaction ref"
        }

        val urlInfo = analyzeUrls(sms)
        when {
            urlInfo.isLookalike -> { spam += 50.0; r += "[SPAM   +50] PHISHING: lookalike domain for '${urlInfo.lookalikeBrand}'" }
            urlInfo.isOfficial  -> { genuine += 10.0; r += "[GENUINE+10] official bank/payment domain URL" }
            urlInfo.hasUrl      -> { spam += 20.0; r += "[SPAM   +20] non-official URL detected" }
        }

        val upperRatio = if (sms.isNotEmpty()) sms.count { it.isUpperCase() }.toDouble() / sms.length else 0.0
        if (upperRatio > 0.35) { spam += 12.0; r += "[SPAM   +12] high caps (${(upperRatio*100).toInt()}%)" }

        val excl = sms.count { it == '!' }
        if (excl >= 2) { spam += (6.0 * excl).coerceAtMost(30.0); r += "[SPAM   +${(6*excl).coerceAtMost(30)}] $excl exclamation marks" }

        val ctaPhrase = Regex("""(install|download|click|register|sign.?up|join|apply)\s*(now|here|today|free|app|link)""",
            RegexOption.IGNORE_CASE)
        if (ctaPhrase.containsMatchIn(lower)) { spam += 30.0; r += "[SPAM   +30] CTA phrase" }

        val gamble = Regex("""(chip|coin|token|tournament|leaderboard|level|spin|slot|wheel|jackpot|satta|matka)""",
            RegexOption.IGNORE_CASE)
        if (gamble.containsMatchIn(lower)) { spam += 20.0; r += "[SPAM   +20] gambling/gaming context" }

        val safetyMsg = Regex("""(do not share|never share|bank (never|will never) ask|not asked for otp)""",
            RegexOption.IGNORE_CASE)
        if (safetyMsg.containsMatchIn(lower)) { genuine += 20.0; r += "[GENUINE+20] security warning phrase" }

        val sn = minOf(spam, 100.0)
        val gn = minOf(genuine, 100.0)
        val diff = sn - gn

        val cat = when {
            urlInfo.isLookalike                          -> SmsCategory.PHISHING_LOOKALIKE
            sn >= 40.0 && diff >= 20.0                   -> SmsCategory.SPAM_PROMOTIONAL
            sn >= 25.0 && diff >= 10.0                   -> SmsCategory.SPAM_PROMOTIONAL
            gn >= 30.0 && diff <= -10.0                  -> SmsCategory.GENUINE_TRANSACTION
            gn >= 20.0                                   -> SmsCategory.GENUINE_TRANSACTION
            lower.contains("otp") ||
            lower.contains("one time password") ||
            lower.contains("do not share")               -> SmsCategory.INFORMATIONAL
            else                                         -> SmsCategory.UNCERTAIN
        }
        val conf = when {
            kotlin.math.abs(diff) >= 45.0 -> "HIGH"
            kotlin.math.abs(diff) >= 20.0 -> "MEDIUM"
            else                          -> "LOW"
        }
        return SpamClassification(cat, sn, gn, conf, r)
    }

    // ── Credit/Debit Classifier ──────────────────────────────────────

    fun classifyTxnType(sms: String, category: SmsCategory): TxnClassification {
        if (category == SmsCategory.BILL_STATEMENT) {
            return TxnClassification(TxnType.PAYMENT_DUE, 0.0, 0.0,
                listOf("[BILL] Statement — PAYMENT_DUE (amount owed, not yet transacted)"))
        }

        val lower  = sms.lowercase()
        val tokens = lower.split(SPLIT_RE).map { it.trim(',') }.filter { it.isNotBlank() }
        val r      = mutableListOf<String>()
        var cs     = 0.0
        var ds     = 0.0

        for (kw in CREDIT_STRONG) {
            if (lower.contains(kw)) { cs += 20.0; r += "[CREDIT+20] '$kw'" }
        }
        for (kw in DEBIT_STRONG) {
            if (lower.contains(kw)) { ds += 20.0; r += "[DEBIT +20] '$kw'" }
        }
        for (tok in tokens) {
            val t = tok.trimEnd('.', ',')
            when (t) {
                in CREDIT_WEAK -> { cs += 4.0; r += "[CREDIT +4] '$t'" }
                in DEBIT_WEAK  -> { ds += 4.0; r += "[DEBIT  +4] '$t'" }
            }
        }

        if (Regex("""\binr\s+dr\.?\b|\brs\.?\s*dr\.?\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            ds += 25.0; r += "[DEBIT +25] 'INR Dr.' / 'Rs Dr.' debit entry"
        }
        if (Regex("""\binr\s+cr\.?\b|\brs\.?\s*cr\.?\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            cs += 25.0; r += "[CREDIT+25] 'INR Cr.' / 'Rs Cr.' credit entry"
        }

        if (Regex("""credit(ed)?\s+(by|for|with|to your|in your|into your)""").containsMatchIn(lower)) {
            cs += 18.0; r += "[CREDIT+18] 'credited by/for/with/to/into your'"
        }
        if (Regex("""debit(ed)?\s+(by|for|from|to|of|against)""").containsMatchIn(lower)) {
            ds += 18.0; r += "[DEBIT +18] 'debited by/for/from/to/of'"
        }
        if (Regex("""(you\s+paid|paid\s+to)\b""").containsMatchIn(lower)) {
            ds += 18.0; r += "[DEBIT +18] 'you paid / paid to'"
        }
        if (Regex("""received\s+from\b""").containsMatchIn(lower)) {
            cs += 18.0; r += "[CREDIT+18] 'received from'"
        }
        if (Regex("""(salary|wages|pension|subsidy|refund|cashback|dividend)\s+(credit|received|added|deposited)""")
            .containsMatchIn(lower)) {
            cs += 15.0; r += "[CREDIT+15] salary/refund/cashback received"
        }
        if (Regex("""(emi|loan|insurance|subscription|bill|utility)\s+(paid|deducted|debited|payment)""")
            .containsMatchIn(lower)) {
            ds += 15.0; r += "[DEBIT +15] EMI/loan/bill payment"
        }
        if (Regex("""(neft|imps|upi|rtgs)\s+(received|credited|incoming)""").containsMatchIn(lower)) {
            cs += 15.0; r += "[CREDIT+15] NEFT/IMPS/UPI received"
        }
        if (Regex("""(neft|imps|upi|rtgs)\s+(sent|debited|transferred|outward)""").containsMatchIn(lower)) {
            ds += 15.0; r += "[DEBIT +15] NEFT/IMPS/UPI sent"
        }
        if (Regex("""(withdrawal|withdrawn)\s+(from|at|via)""").containsMatchIn(lower)) {
            ds += 15.0; r += "[DEBIT +15] withdrawal from/at/via"
        }
        if (lower.contains("credit card") && !lower.contains("credited") && !lower.contains("credit received")) {
            cs = (cs - 20.0).coerceAtLeast(0.0)
            r += "[CREDIT-20] 'credit card' noun removed from credit score"
        }

        // UPI bank credit SMS format: "credited by Rs.X ... debited to VPA sender@handle"
        // "debited to VPA/UPI" identifies the sender's UPI address — it is NOT a debit on our account.
        // Cancel the false debit signals injected by "debited" keyword (+20) and "debited to" regex (+18).
        if (Regex("""credited\b.*\bdebited\s+to\s+(vpa|upi)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            ds = (ds - 38.0).coerceAtLeast(0.0)
            r += "[DEBIT-38] 'debited to VPA/UPI' in credited SMS = UPI sender reference, cancelling false debit signals"
        }

        val type = when {
            cs > ds + 5.0          -> TxnType.CREDIT
            ds > cs + 5.0          -> TxnType.DEBIT
            cs == 0.0 && ds == 0.0 -> TxnType.UNKNOWN
            else                   -> TxnType.UNKNOWN
        }
        return TxnClassification(type, cs, ds, r)
    }

    // ── Public API ───────────────────────────────────────────────────

    fun extract(sms: String): SmsExtractionResult {
        val norm     = normalize(sms)
        val tokens   = tokenize(norm)
        val spam     = classifySpam(sms)
        val txn      = classifyTxnType(sms, spam.category)
        val dueDate  = if (spam.category == SmsCategory.BILL_STATEMENT) extractDueDate(sms) else null

        val raw     = extractCandidates(norm, tokens)
        val scored  = raw.map { scoreCandidate(it, tokens) }

        val eligible = scored.filter { disqualifyReason(it, tokens) == null }
        val disqed   = scored.filter { disqualifyReason(it, tokens) != null }.map { c ->
            val reason = disqualifyReason(c, tokens) ?: ""
            c.copy(score = -999.0, reasons = c.reasons + "DISQUALIFIED: $reason")
        }

        val sortedE = eligible.sortedByDescending { it.score }
        val winner  = sortedE.firstOrNull()?.takeIf { it.score > -5.0 }

        val secondaryAmount = if (spam.category == SmsCategory.BILL_STATEMENT) {
            sortedE.drop(1).firstOrNull()?.takeIf { it.score > -20.0 }
        } else null

        val all = (sortedE + disqed).sortedByDescending { it.score }
        return SmsExtractionResult(sms, spam, txn, winner, secondaryAmount, dueDate, all)
    }
}
