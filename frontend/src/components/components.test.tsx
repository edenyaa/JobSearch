import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, test, vi } from 'vitest'
import { ConfirmButton } from './ConfirmButton'
import { CoverageBar } from './CoverageBar'
import { FileInput } from './FileInput'
import { ErrorMessage, Loading } from './ErrorMessage'
import { StatusBadge } from './StatusBadge'
import { TagInput } from './TagInput'

function TagHarness({ initial = [] as string[] }) {
  const [tags, setTags] = useState<string[]>(initial)
  return (
    <div>
      <TagInput label="Keywords" value={tags} onChange={setTags} />
      <output aria-label="current">{tags.join('|')}</output>
    </div>
  )
}

describe('TagInput', () => {
  test('GivenTypedText_WhenPressingEnter_ThenTagIsAddedAndInputCleared', async () => {
    const user = userEvent.setup()
    render(<TagHarness />)

    await user.type(screen.getByLabelText('Keywords'), 'Spring{Enter}')

    expect(screen.getByLabelText('current')).toHaveTextContent('Spring')
    expect(screen.getByLabelText('Keywords')).toHaveValue('')
  })

  test('GivenTypedText_WhenTypingComma_ThenTagIsAdded', async () => {
    const user = userEvent.setup()
    render(<TagHarness />)

    await user.type(screen.getByLabelText('Keywords'), 'REST,')

    expect(screen.getByLabelText('current')).toHaveTextContent('REST')
  })

  test('GivenExistingTag_WhenAddingSameTagInOtherCase_ThenItIsIgnored', async () => {
    const user = userEvent.setup()
    render(<TagHarness initial={['Java']} />)

    await user.type(screen.getByLabelText('Keywords'), 'java{Enter}')

    expect(screen.getByLabelText('current')).toHaveTextContent(/^Java$/)
  })

  test('GivenBlankText_WhenPressingEnter_ThenNothingIsAdded', async () => {
    const user = userEvent.setup()
    render(<TagHarness />)

    await user.type(screen.getByLabelText('Keywords'), '   {Enter}')

    expect(screen.getByLabelText('current')).toBeEmptyDOMElement()
  })

  test('GivenTag_WhenClickingRemove_ThenItIsRemoved', async () => {
    const user = userEvent.setup()
    render(<TagHarness initial={['Java', 'Docker']} />)

    await user.click(screen.getByRole('button', { name: 'Remove Java' }))

    expect(screen.getByLabelText('current')).toHaveTextContent(/^Docker$/)
  })

  test('GivenEmptyInput_WhenPressingBackspace_ThenLastTagIsRemoved', async () => {
    const user = userEvent.setup()
    render(<TagHarness initial={['Java', 'Docker']} />)

    await user.click(screen.getByLabelText('Keywords'))
    await user.keyboard('{Backspace}')

    expect(screen.getByLabelText('current')).toHaveTextContent(/^Java$/)
  })

  test('GivenPendingText_WhenInputLosesFocus_ThenTagIsAdded', async () => {
    const user = userEvent.setup()
    render(<TagHarness />)

    await user.type(screen.getByLabelText('Keywords'), 'Kubernetes')
    await user.tab()

    expect(screen.getByLabelText('current')).toHaveTextContent('Kubernetes')
  })
})

describe('ConfirmButton', () => {
  test('GivenButton_WhenClickedOnce_ThenAskForConfirmationWithoutCallingBack', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(<ConfirmButton label="Delete" onConfirm={onConfirm} />)

    await user.click(screen.getByRole('button', { name: 'Delete' }))

    expect(screen.getByRole('button', { name: 'Confirm delete' })).toBeInTheDocument()
    expect(onConfirm).not.toHaveBeenCalled()
  })

  test('GivenConfirmation_WhenConfirming_ThenCallbackRunsAndButtonResets', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(<ConfirmButton label="Delete" onConfirm={onConfirm} />)

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete' }))

    expect(onConfirm).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
  })

  test('GivenConfirmation_WhenCancelling_ThenCallbackDoesNotRun', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()
    render(<ConfirmButton label="Delete" onConfirm={onConfirm} />)

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onConfirm).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
  })

  test('GivenDisabledButton_WhenRendering_ThenItCannotBeClicked', () => {
    render(<ConfirmButton label="Delete" onConfirm={vi.fn()} disabled />)

    expect(screen.getByRole('button', { name: 'Delete' })).toBeDisabled()
  })
})

describe('StatusBadge', () => {
  test('GivenStatus_WhenRendering_ThenFriendlyLabelAndClassAreShown', () => {
    render(<StatusBadge status="INTERVIEW" />)

    expect(screen.getByText('Interview')).toHaveClass('badge-interview')
  })
})

describe('CoverageBar', () => {
  test('GivenPercent_WhenRendering_ThenProgressBarExposesTheValue', () => {
    render(<CoverageBar label="Coverage" percent={72} />)

    const bar = screen.getByRole('progressbar', { name: 'Coverage' })
    expect(bar).toHaveAttribute('aria-valuenow', '72')
    expect(screen.getByText('72%')).toBeInTheDocument()
  })
})

describe('ErrorMessage and Loading', () => {
  test('GivenMessage_WhenRendering_ThenItIsAnnouncedAsAlert', () => {
    render(<ErrorMessage message="Something broke" />)

    expect(screen.getByRole('alert')).toHaveTextContent('Something broke')
  })

  test('GivenNoMessage_WhenRendering_ThenNothingIsShown', () => {
    render(<ErrorMessage message={null} />)

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  test('GivenLoading_WhenRendering_ThenStatusRoleIsUsed', () => {
    render(<Loading />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading…')
  })
})

describe('FileInput', () => {
  test('GivenNoFileSelected_WhenRendering_ThenEnglishChooseFileButtonAndPlaceholderAreShown', () => {
    render(<FileInput id="f" label="Upload CV" accept=".pdf" onSelect={vi.fn()} />)

    expect(screen.getByText('Choose file')).toBeInTheDocument()
    expect(screen.getByText('No file chosen')).toBeInTheDocument()
  })

  test('GivenFile_WhenSelecting_ThenItsNameIsShownAndCallbackReceivesTheFile', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    render(<FileInput id="f" label="Upload CV" accept=".pdf,.txt" onSelect={onSelect} />)
    const file = new File(['hello'], 'my-cv.txt', { type: 'text/plain' })

    await user.upload(screen.getByLabelText('Upload CV'), file)

    expect(screen.getByText('my-cv.txt')).toBeInTheDocument()
    expect(screen.queryByText('No file chosen')).not.toBeInTheDocument()
    expect(onSelect).toHaveBeenCalledWith(file)
  })

  test('GivenInput_WhenRendering_ThenItKeepsTheAcceptFilterAndIsLabelled', () => {
    render(<FileInput id="f" label="Upload CV" accept=".pdf,.docx,.txt" onSelect={vi.fn()} />)

    expect(screen.getByLabelText('Upload CV')).toHaveAttribute('accept', '.pdf,.docx,.txt')
    expect(screen.getByLabelText('Upload CV')).toHaveAttribute('type', 'file')
  })

  test('GivenChooseFileButton_WhenClickingItsLabel_ThenTheSameInputIsTargeted', () => {
    render(<FileInput id="f" label="Upload CV" accept=".pdf" onSelect={vi.fn()} />)

    expect(screen.getByText('Choose file').closest('label')).toHaveAttribute('for', 'f')
  })
})
