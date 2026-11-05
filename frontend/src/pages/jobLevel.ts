import type { Job } from '../api/types'

export type JobLevel = 'student' | 'junior' | 'other'

const STUDENT_TITLE = /\b(?:students?|interns?|internships?)\b|סטודנט|סטאז/i
const JUNIOR_TITLE =
  /\b(?:junior|jr|graduates?|grad|new grad|entry[- ]level|trainee|apprentice|early career|no experience)\b|ג'וניור|בוגר/i
const STUDENT_TEXT =
  /\b(?:student position|student role|working student|for students|currently (?:a )?students?|studying (?:for|towards))\b|סטודנט/i
const JUNIOR_TEXT =
  /\b(?:no (?:prior |previous |relevant )?experience (?:is )?(?:needed|required|necessary)|entry[- ]level|fresh(?:ly)? graduates?|recent graduates?|new grads?|0\s*(?:-|–|to)\s*[12] years?|up to (?:1|2) years?)\b/i

/**
 * Which kind of beginner job this is, read from the title first and then from the start of the description:
 * a student or intern job, a junior, graduate or no-experience job, or neither.
 */
export function levelOf(job: Pick<Job, 'title' | 'description'>): JobLevel {
  const title = job.title ?? ''
  if (STUDENT_TITLE.test(title)) return 'student'
  if (JUNIOR_TITLE.test(title)) return 'junior'
  const text = (job.description ?? '').slice(0, 2500)
  if (STUDENT_TEXT.test(text)) return 'student'
  if (JUNIOR_TEXT.test(text)) return 'junior'
  return 'other'
}
