import { describe, expect, test } from 'vitest'
import { levelOf } from './jobLevel'

const level = (title: string | null, description = '') => levelOf({ title, description })

describe('levelOf', () => {
  test.each(['Software Student', 'QA Intern', 'Software Engineering Internship', 'Working Student - Backend', 'סטודנט/ית לפיתוח'])(
    'GivenTitle%s_WhenClassifying_ThenStudent',
    (title) => expect(level(title)).toBe('student'),
  )

  test.each(['Junior QA Engineer', 'Graduate Developer', 'Entry Level Backend', 'Jr. Java Developer', 'Trainee Tester', 'New Grad SWE', 'ג׳וניור'.replace('׳', "'") + ' מפתח'])(
    'GivenTitle%s_WhenClassifying_ThenJunior',
    (title) => expect(level(title)).toBe('junior'),
  )

  test('GivenInternalOrInternational_WhenClassifying_ThenNotAnIntern', () => {
    expect(level('Internal Tools Engineer')).toBe('other')
    expect(level('International Sales')).toBe('other')
  })

  test('GivenPlainTitleWithNoExperienceNeeded_WhenClassifying_ThenJunior', () => {
    expect(level('QA Engineer', 'No experience required. We will train you.')).toBe('junior')
    expect(level('Developer', 'Looking for 0-1 years of experience')).toBe('junior')
  })

  test('GivenPlainTitleForStudents_WhenClassifying_ThenStudent', () => {
    expect(level('Developer', 'This is a student position, 20 hours a week.')).toBe('student')
  })

  test('GivenTitleBeatsDescription_WhenClassifying_ThenTitleWins', () => {
    expect(level('Junior Developer', 'Great for students too')).toBe('junior')
    expect(level('Student Developer', 'No experience required')).toBe('student')
  })

  test('GivenOrdinaryJob_WhenClassifying_ThenOther', () => {
    expect(level('Backend Developer', 'You have 5 years of Java experience.')).toBe('other')
    expect(level(null)).toBe('other')
  })
})
